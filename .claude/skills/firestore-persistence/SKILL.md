---
name: firestore-persistence
description: Conventions for the Cloud Firestore storage layer of the Movie API - document layout, deterministic ids, the two allowed operations (create, read all), the in-memory MovieCatalog that loads from it, seed data and free-tier cost. Use whenever code touches MovieRepository, FirestoreMovieRepository, MovieDocument, MovieCatalog, seed data, or the user mentions Firestore, database, storage, persistence or read costs.
---

# Firestore storage for the Movie API

Firestore is the **durable store only**. Searching happens in memory (ADR-0007; see the `movie-search` skill).
That keeps the adapter tiny and means there are no filtered queries and no composite indexes to create or maintain.
The movie model itself (fields and validation) is defined in AGENTS.md and ADR-0006.

## Layout
- Collection `movies`, one document per movie, in the `(default)` database (named databases get no free quota).
- Document id = deterministic hash of `normalize(name) + "|" + year` (`MovieId.derivedFrom`: the first 32 lowercase hex characters of SHA-256 over UTF-8; frozen, see ADR-0006). Creating with `DocumentReference.create(...)` fails with `ALREADY_EXISTS` if the id is taken, which gives atomic duplicate detection without a transaction. Translate that error to `DuplicateMovieException`.
- Fields (class `MovieDocument`), stored as entered after trimming:
  `name`, `year`, `genres` (list of strings), `ageLimit`, `rating`, `director` (map with `firstName`, `lastName`, or absent), `actors` (list of such maps, order preserved), `synopsis`, `createdAt`.
- No derived search fields are stored. Normalization is done in memory when searching.
- `normalize` = `TextNormalization.normalize`: Unicode NFC, `strip()`, collapse `\p{javaWhitespace}+` to one space, lowercase using `Locale.ROOT` (NBSP is not whitespace). Never write a second version.
- Document the collection and fields in `docs/firestore-model.md`.

## The only two operations (`MovieRepository`)
- `create(Movie)`: `DocumentReference.create(...)`; map `ALREADY_EXISTS` to `DuplicateMovieException`.
- `findAll()`: read the whole `movies` collection, map each document with `MovieDocument`.
`MovieRepository` has no `findById`: get by id reads from the `MovieCatalog` snapshot, so it costs no reads and sees the same data as search (ADR-0007).
Do not add filtered queries, `orderBy`, `offset` or indexes. If a feature seems to need one, it belongs in `MovieSearch` instead (ADR-0007).

## Client setup (`common/FirestoreConfig`)
Build one `Firestore` bean from `FirestoreOptions.getDefaultInstance()`. On Cloud Run it uses Application Default Credentials (the runtime service account) and discovers the project automatically.
Do not add key files or project-specific settings. Keep this class trivial.

## Catalog (`MovieCatalog`, in the service package)
Policy and cost reasoning: ADR-0013.
- Holds an immutable snapshot (movies keyed by id) in an `AtomicReference`; the next reload time (`nextLoadAttemptAt`) is kept separately.
- Loads via `MovieRepository.findAll()` in its constructor. If the first load fails, startup fails loudly (usually a permission or database-creation problem). The refresh interval must be positive.
- After a successful create, `MovieService` calls `include(movie)`, which publishes a new snapshot with the movie, replacing any entry with the same id, and does not move the next reload. (Named `include`, not `add`: SpotBugs treats `add*`/`load*`/`set*`/`put*` as mutators and flags the injected collaborators with EI_EXPOSE_REP2.)
- Reloads lazily, on the request thread, when the injected `Clock` reaches `nextLoadAttemptAt` = start of the last attempt + `app.catalog.refresh-interval` (default 60 minutes). No scheduler: an idle instance costs no reads. Only one reload runs at a time (non-reentrant `AtomicBoolean`, re-checked after taking it); other requests serve the current copy. Movies included during a reload are merged into the fresh snapshot.
- If a refresh fails, keep serving the old snapshot, log a warning and retry only after a **full interval**: a failed load can be billed for every document it read.
- `movies()` and `findById()` run the refresh check. Get by id looks the movie up in the same snapshot (`MovieService.findMovie`, 404 when absent).

## Seed data
`MovieSeedLoader` (in `movie/service`, an `ApplicationRunner`) reads `src/main/resources/seed/movies.json` (the 20 movies from the provided `movies-compact.json`) when `app.seed.enabled=true` and the catalog is empty.
Each entry goes through `MovieService.createMovie`, which trims, validates, detects duplicates and updates the catalog. Entries are converted one at a time with an `ObjectReader` from the strict application mapper (ADR-0012). Unconvertible, invalid or duplicate entries are logged at WARN with their index and skipped, never fatal. A `MovieStorageException` stops seeding (logged, startup continues; waiting for every entry could exceed Cloud Run's startup limit). An unreadable or non-array seed file fails startup. A partly seeded catalog is not resumed on the next startup (it is no longer empty).
The file has suspected mistakes (stray spaces, typos, wrong years): report them to the user, do not correct them silently.

## Testing
There is no emulator or integration-test suite (ADR-0003). Therefore:
- Keep `FirestoreMovieRepository` thin: two methods plus mapping through `MovieDocument`. Put anything with real logic (normalization, id generation, document mapping) in small pure classes with unit tests.
- `MovieCatalog`, `MovieService` and the seed loader are unit tested with a mocked `MovieRepository` / `MovieService` and a fixed `Clock`.
- The adapter is verified by the Postman collection after each deploy.

## Cost awareness (free tier)
The project's free database includes 50,000 reads, 20,000 writes and 20,000 deletes per day and 1 GiB storage.
Every catalog load reads every movie once, so reads/day = (cold starts + refreshes during an instance's life + failed attempts) x max(movies, 1). Loads never happen per request.
With `min-instances 0`, sparse traffic can cause 50-100 cold starts a day, so plan for about 100 loads a day: keep the catalog under about 500 movies (ADR-0013) and keep Cloud Run at `--max-instances 1`. Seeding 20 movies costs 20 writes, once.
Check current numbers on the Firestore pricing page, since quotas can change.

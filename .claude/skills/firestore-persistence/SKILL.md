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
- Holds an immutable snapshot (list of movies plus the load time) in an `AtomicReference`.
- Loads via `MovieRepository.findAll()` at startup. If the first load fails, let startup fail loudly (usually a permission or database-creation problem).
- After a successful create, publish a new snapshot that includes the movie (keep the original load time).
- Reloads when the snapshot is older than `app.catalog.refresh-interval` (default 60 minutes), using an injected `Clock`. Only one thread reloads at a time. If a refresh fails, keep serving the old snapshot, log a warning and retry at most once a minute.
- `isEmpty()` serves the seed loader; `MovieCatalog.findById(id)` looks the movie up in the snapshot (it triggers the same refresh check as search).

## Seed data
`MovieSeedLoader` (in `movie/service`, an `ApplicationRunner`) reads `src/main/resources/seed/movies.json` (the 20 movies from the provided `movies-compact.json`) when `app.seed.enabled=true` and the catalog is empty.
Each entry goes through `MovieService.createMovie`, which trims, validates, detects duplicates and updates the catalog. Invalid or duplicate entries are logged and skipped, never fatal.
The file has suspected mistakes (stray spaces, typos, wrong years): report them to the user, do not correct them silently.

## Testing
There is no emulator or integration-test suite (ADR-0003). Therefore:
- Keep `FirestoreMovieRepository` thin: two methods plus mapping through `MovieDocument`. Put anything with real logic (normalization, id generation, document mapping) in small pure classes with unit tests.
- `MovieCatalog`, `MovieService` and the seed loader are unit tested with a mocked `MovieRepository` / `MovieService` and a fixed `Clock`.
- The adapter is verified by the Postman collection after each deploy.

## Cost awareness (free tier)
The project's free database includes 50,000 reads, 20,000 writes and 20,000 deletes per day and 1 GiB storage.
Every catalog load reads every movie once, so cost = (number of loads per day) x (number of movies). Loads happen on cold start, after the refresh interval, and never per request.
Keep the catalog under about 1,000 movies and keep Cloud Run at `--max-instances 1`. Seeding 20 movies costs 20 writes, once.
Check current numbers on the Firestore pricing page, since quotas can change.

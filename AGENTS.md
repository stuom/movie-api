# Movie API

REST service to **list**, **search** and **add** movies. Pay attention to clarity. Tests and documentation matter more than the number of features.

Base package: `com.pehmo.movieapi` (Java package names cannot contain hyphens; the Gradle project and artifact name stays `movie-api`).

## Tech stack
- Java 21, Spring Boot 3.x (Spring MVC), **Gradle** (Kotlin DSL). Always use the wrapper `./gradlew`, never a global `gradle`.
  Run Gradle with a Java 21 JDK as `JAVA_HOME` (or list it in `org.gradle.java.installations.paths` in your user `~/.gradle/gradle.properties`); the build uses a Java 21 toolchain.
  Dependency and plugin versions are declared directly in `build.gradle.kts` (no version catalog); the Spring Boot BOM, imported as a Gradle `platform(...)`, supplies versions for the Spring libraries
  (not the `io.spring.dependency-management` plugin, which also overrides the quality tools' own classpaths, ADR-0008).
- springdoc-openapi (Swagger UI). The movie body is validated in plain Java by `MovieRules`, not with Bean Validation (ADR-0009).
- **Persistence: Cloud Firestore** (Native mode, `(default)` database, free tier) as a durable store only, accessed with the official
  `com.google.cloud:google-cloud-firestore` Java client (Spring Data Firestore is reactive-only and not used, ADR-0003).
  **Search runs in memory** over a cached copy of the catalog (ADR-0007).
- Tests: JUnit 5, Mockito, AssertJ, `@WebMvcTest` (unit tests only, see Testing conventions)
- Quality gates: compiler warnings as errors, Spotless (google-java-format), Checkstyle, SpotBugs, JaCoCo (ADR-0008)
- Deployment: Google Cloud Run, project `movie-api-511411`, container built from `Dockerfile`

## Commands
| Task | Command |
|---|---|
| Full quality gate (compile with `-Werror`, format check, lint, bug detection, unit tests, coverage) | `./gradlew check` |
| Unit tests only (fast) | `./gradlew test` |
| Auto-format code | `./gradlew spotlessApply` |
| Coverage report | `./gradlew jacocoTestReport` (output in `build/reports/jacoco`) |
| Deploy to Cloud Run (runs `check` first) | `./gradlew deployCloudRun` |

Unit tests are named `*Test` and live in `src/test`. Always finish a task with `./gradlew check`.
By design there is no local-run, emulator or integration-test setup: behavior is covered by unit tests, and the deployed service is verified with the Postman collection in `postman/` (see Testing conventions).

## Architecture
Package by feature, layered inside the feature. Dependencies point inward only:
`web -> service -> persistence`. The service depends on the `MovieRepository` **interface**, never on Firestore classes.
Search logic lives in the service layer as plain Java, so it is fully unit-testable.

```
build.gradle.kts, settings.gradle.kts, gradle.properties   build and deployment settings
gradlew, gradlew.bat, gradle/wrapper/                      Gradle wrapper (pinned version and checksum)
config/checkstyle/                                         Checkstyle rules and test suppressions (layout is left to google-java-format)
Dockerfile, .dockerignore, .gcloudignore                   container build for Cloud Run
src/main/java/com/pehmo/movieapi/
  MovieApiApplication.java
  movie/
    web/          MovieController, NewMovieRequest, MovieResponse, MoviePageResponse, PersonDto, MovieMapper
    service/      MovieService (business rules), Movie and Person (domain records),
                  NewMovie (raw input, all fields nullable), MovieRules (the only home of the movie rules, ADR-0009),
                  InvalidMovieException + FieldViolation, MovieId (deterministic id), TextNormalization (normalize, trim),
                  DuplicateMovieException, MovieStorageException (storage failures without storage types, ADR-0010),
                  MovieSearchCriteria (sealed: exactly one search field or none), MovieSearch (matching, sorting, paging), MoviePage,
                  MovieCatalog (in-memory snapshot of all movies), MovieSeedLoader (loads the sample data on startup)
    persistence/  MovieRepository (interface), FirestoreMovieRepository, MovieDocument (storage mapping),
                  FirestoreErrors (recognizes ALREADY_EXISTS)
  common/         error handling (@RestControllerAdvice), Firestore configuration
src/main/resources/seed/movies.json   sample data (the provided movies-compact.json)
src/test/java/                     unit tests (*Test)
postman/                           Postman collection + environment (API tests against the deployed service)
docs/  api.md, deployment.md, firestore-model.md, adr/, ai-log/
```

Rules:
- Controllers contain no business logic; they map HTTP <-> DTO and delegate to the service.
- Firestore types (`DocumentSnapshot`, `Timestamp`, ...) never leave the `persistence` package (the one exception is `common/FirestoreConfig`, which builds the client). The API exposes DTO `record`s only.
- Constructor injection only; no field `@Autowired`. Prefer immutable objects.
- No new dependency without asking the user first (record the reason in an ADR).
- Never disable or loosen a quality gate to get a green build.

## Domain (derived from the provided sample data `movies-compact.json`; change only via ADR-0006)
`Movie`:

| Field | Type | Rules |
|---|---|---|
| `id` | string | Generated and deterministic: hash of normalized `name` + `year`. Never supplied by clients. |
| `name` | string | Required, 1-200 chars. |
| `year` | integer | Required, 1888..current year + 5. |
| `genres` | list of strings | Required, 1-5 entries, each 1-50 chars. Free text for now (no fixed vocabulary). |
| `ageLimit` | integer | Required, 0-18: minimum recommended age in years. |
| `rating` | integer | Required, 1-5 stars. |
| `director` | `Person` | Optional. |
| `actors` | list of `Person` | Optional, 0-30 entries, order preserved. |
| `synopsis` | string | Optional, max 2000 chars. |

`Person`: `firstName` and `lastName`, both required, 1-100 chars. Multi-word last names are normal (`Lee Jones`, `del Toro`).

Rules:
- All incoming text is trimmed (the sample data has stray leading spaces, for example `" Downey Jr."`).
- Normalization (`TextNormalization.normalize`) = Unicode NFC, trim, collapse inner whitespace, lowercase with `Locale.ROOT`. It is used for the id and for all text matching.
  Whitespace means `Character.isWhitespace` everywhere (so U+00A0 NBSP is not whitespace). Stored text is only trimmed. The normalization and id algorithm are frozen (ADR-0006).
- All movie rules live in `MovieRules` (plain Java, ADR-0009): it reports every violation with its field path. Do not duplicate them as Bean Validation annotations on DTOs.
- Duplicate rule: same normalized `name` + `year` is rejected with `409 Conflict` (the deterministic id makes this atomic).
- Every field is searchable (see API conventions). Matching text is normalized in memory at search time; nothing extra is stored.

## Sample data
- `movies-compact.json` (20 movies, provided by the user) defines the data shape. It is an array of objects with the same shape as the `POST` body. Keep a copy at `src/main/resources/seed/movies.json`.
- `MovieSeedLoader` runs on startup when `app.seed.enabled=true` and the loaded `MovieCatalog` is empty. Deleting every movie therefore brings the sample data back on the next startup; set `app.seed.enabled=false` if that is unwanted.
  It passes each entry through `MovieService.createMovie`, so seed data gets the same validation, trimming and duplicate check as API input. Invalid or duplicate entries are logged and skipped.
- The file contains likely mistakes (typos, wrong release years). Do not silently correct them: list the suspected errors for the user and let them decide.

## API conventions
- Base path `/api/v1`. Resource names are plural nouns: `/api/v1/movies`.
- `GET /api/v1/movies` lists and searches movies. **Use at most one search field per request**; with no search field it lists everything (ADR-0002, ADR-0007). All parameters are optional.

| Parameter | Type | Matches |
|---|---|---|
| `name` | text | Movie name contains the text (case-insensitive). |
| `minYear` | integer | `year >= minYear` (inclusive). |
| `maxYear` | integer | `year <= maxYear` (inclusive). Use with or without `minYear`; `minYear` must not exceed `maxYear`. For one exact year, set both to the same value. |
| `genre` | text | One of the movie's genres equals the text (case-insensitive). One genre per request. |
| `viewerAge` | integer 0-120 | Movies this viewer may watch: `ageLimit <= viewerAge`. |
| `minRating` | integer 1-5 | `rating >= minRating` (inclusive). |
| `maxRating` | integer 1-5 | `rating <= maxRating` (inclusive). Use with or without `minRating`; `minRating` must not exceed `maxRating`. |
| `director` | text | The director's first name or last name contains **every word** of the text (case-insensitive). |
| `actor` | text | At least one actor whose first name or last name contains **every word** of the text. All words must match the **same** actor. |
| `synopsis` | text | The synopsis contains **every word** of the text, in any order (case-insensitive). |
| `page`, `size` | integers | Page number (0-based, default 0) and page size (default 20, max 100). |

- One field means one row of this table, except that `minYear` + `maxYear` count together as one field (year) and `minRating` + `maxRating` count together as one field (rating). `page` and `size` are not search fields. Sending two or more different search fields returns `400` naming them; filters are never combined.
- A *word* is a whitespace-separated part of the text. Text parameters are trimmed; a blank value counts as not provided. Accents are not folded.
- Response: `{ "items": [...], "page": 0, "size": 20, "totalItems": 57, "totalPages": 3 }`. Order is fixed: name ascending (case-insensitive), then id. Items contain all movie fields.
- Examples (one field each): `GET /api/v1/movies?viewerAge=12`, `GET /api/v1/movies?minRating=4`, `GET /api/v1/movies?minYear=2000&maxYear=2009`, `GET /api/v1/movies?genre=sci-fi`, `GET /api/v1/movies?actor=harrison ford`, `GET /api/v1/movies?director=spielberg`, `GET /api/v1/movies?synopsis=missing father`.
- `GET /api/v1/movies/{id}` fetch one (from the catalog, like search). `POST /api/v1/movies` create -> `201 Created` + `Location` header + created body.
  There is deliberately no update or delete endpoint: the service is public and unauthenticated, so it only lists, searches and adds (ADR-0006).
  Example `POST` body:
  ```json
  {"name": "Aliens", "year": 1986, "genres": ["Action", "Adventure", "Sci-fi"], "ageLimit": 16, "rating": 5,
   "director": {"firstName": "James", "lastName": "Cameron"},
   "actors": [{"firstName": "Sigourney", "lastName": "Weaver"}],
   "synopsis": "Ellen Ripley is rescued by a deep salvage team after being in hypersleep for 57 years."}
  ```
- Errors use RFC 7807 `ProblemDetail`: `400` for invalid input (bad body, `viewerAge` or ratings out of range, `minRating` above `maxRating`, `minYear` above `maxYear`, more than one search field, bad `page`/`size`), `404` not found, `409` duplicate. Never leak stack traces.
- Every endpoint is annotated for OpenAPI (summary, parameters, response codes) and described in `docs/api.md`. Adding a filter means updating this table, `MovieSearchCriteria`, `MovieSearch`, the OpenAPI annotations, `docs/api.md` and the tests (see the `movie-search` skill).

## Persistence and catalog rules (details in the `firestore-persistence` and `movie-search` skills)
- Firestore is the **durable store only**. Allowed operations: create one movie (atomic, via the deterministic id), read all movies. Get by id reads from `MovieCatalog`, not from Firestore. Do not add filtered Firestore queries or composite indexes: all searching happens in memory (ADR-0007).
- `MovieCatalog` holds an immutable in-memory snapshot of all movies. It loads on startup, is replaced after each successful create, and reloads when older than `app.catalog.refresh-interval` (default 60 minutes).
- Cost and scale: every load reads every movie document, and the free tier allows 50,000 reads per day. Keep the catalog under about 1,000 movies; beyond that, change the storage/search approach (ADR-0007).
- Keep `--max-instances 1` on Cloud Run: every instance has its own catalog, so more instances could show different data until their next refresh.
- `actors` and `director` are embedded in the movie document (ADR-0006).
- Use the `(default)` database only: the free tier does not apply to named databases.

## Naming and code style
- Names state intent, not mechanism. Good: `findMoviesMatching(MovieSearchCriteria)`, `createMovie(NewMovie)`.
  Bad: `process`, `handle`, `doIt`, `data`, `tmp`, `util`.
- Classes: `MovieController`, `MovieService`, `MovieRepository`, `FirestoreMovieRepository`, `MovieResponse`, `NewMovieRequest`, `Person`, `PersonDto`.
- Booleans read as questions (`isDuplicate`). No abbreviations except well-known ones (`id`, `dto`).
- Javadoc on every public class and method; explain *why* and any non-obvious rules, not what the code literally does.
- Methods stay small and do one thing. Prefer early returns over deep nesting. No commented-out code.
- Custom exceptions carry meaning: `MovieNotFoundException`, `DuplicateMovieException`, `InvalidMovieException`, `InvalidSearchCriteriaException`.

## Testing conventions
- Test names: `shouldReturn404WhenMovieDoesNotExist`. Body follows Given / When / Then with comments.
- One behavior per test. No `Thread.sleep`, no dependence on test order or on seed data not created by the test.
- Build test data with a builder/factory (`MovieTestData.aMovie().withName(...)`), not copy-pasted literals.
- Unit tests only: service with Mockito (mock `MovieRepository`); controller with `@WebMvcTest`; `MovieSearch` (the core: each search field, paging, ordering), `MovieSearchCriteria` (the one-field rule and validation), `MovieRules` (every boundary, fixed `Clock`), normalization, id generation (golden ids computed outside the code), document mapping, `MovieCatalog` (refresh logic with a mocked repository and a fixed `Clock`) and `MovieSeedLoader` (mocked `MovieService`) directly.
- Keep `FirestoreMovieRepository` and `FirestoreConfig` thin (query building and mapping only): they have no automated tests and are excluded from the JaCoCo rule.
  `MovieApiApplication` is excluded too: it only bootstraps Spring, so keep it free of logic (ADR-0008).
- API tests: the Postman collection in `postman/` (add a movie, list all movies, search with one search field at a time) is written by the `postman-test-writer` agent and run by hand against the deployed service. It is not part of `./gradlew check`. Each run adds one test movie (name starts with `0 Pmtest`) to the real database, and there is no delete endpoint, so leftovers are removed by hand in the Firestore console.
- JaCoCo minimum: 80% line coverage on the remaining `main` code. Meaningful assertions matter more than the number.
  `check` fails if main code falls under the rule but no test ran (JaCoCo alone would silently skip the rule, ADR-0008).

## Deployment (Google Cloud Run)
- One-line deploy: `./gradlew deployCloudRun`. It runs `check`, then `gcloud run deploy --source .`; Cloud Build builds the `Dockerfile`.
- All deployment settings come from `gradle.properties` (`gcpProjectId`, `gcpRegion`, `cloudRunService`, `cloudRunServiceAccount`) and the task in `build.gradle.kts`.
  Change them there, not in ad-hoc commands, so deployments stay reproducible.
- Keep `--max-instances 1` (catalog consistency, ADR-0007).
- The service runs as a dedicated service account with only the `roles/datastore.user` role (least privilege). No key files, ever: Application Default Credentials do the work.
- The app must read its port from `$PORT` (`server.port=${PORT:8080}`).
- Deployment and Firestore setup create billable or persistent cloud resources: show the user the command and wait for a clear yes before running it.
  Details and one-time setup are in the `deploy-cloud-run` skill and `docs/deployment.md`.

## Documentation
- `README.md`: what it is, prerequisites (Java 21), how to run the unit tests and the Postman collection, link to the deployed Swagger UI, link to the deployment doc.
- `docs/api.md`: human-readable contract with request/response examples (curl).
- `docs/firestore-model.md`: collection layout and document fields. `docs/api.md` also documents the search semantics.
- `docs/adr/NNNN-title.md`: one short ADR per significant decision. ADR-0001 to ADR-0010 exist (index in `docs/adr/README.md`); update or supersede them instead of duplicating.
- `docs/ai-log/NN-slice-name.md`: record of each AI-assisted slice: key prompts, plan, what was changed or rejected.

## How to work (agents and humans)
1. Read this file and the relevant `docs/` before changing anything.
2. For any non-trivial task: propose a short plan first and wait for approval. Ask when requirements are ambiguous.
3. Work in small vertical slices (one use case). Contract first, then tests, then implementation.
4. After each slice run `./gradlew check`, then request a `code-reviewer` review, fix "Must fix" findings, update docs.
5. Commit once per slice with a clear message (`feat(movies): add search filters`).

## Definition of done
- `./gradlew check` passes with no new warnings.
- New behavior has unit tests (each new search field: matching, non-matching and edge cases, plus the one-field rule). After a deploy, the Postman collection passes.
- OpenAPI annotations, `docs/api.md`, `docs/firestore-model.md`, Javadoc and README are current.
- An ADR exists for every significant design decision. The AI log entry for the slice is written.

## Boundaries
- Ask first: running the Postman collection against the deployed service (each run adds one test movie to the real database), new dependencies, changes to the public API contract, the domain model, search semantics, any cloud command that creates or changes resources.
- Never: commit secrets or credentials, disable linters or tests to get a green build, expose Firestore types in the API, swallow exceptions silently, add filtered Firestore queries or indexes (search stays in memory).

## Project agents and skills
Agents (`.claude/agents/`): `architect`, `unit-test-writer`, `postman-test-writer`, `doc-writer`, `code-reviewer`.
Skills (`.claude/skills/`): `add-endpoint` (end-to-end slice workflow), `firestore-persistence`, `movie-search`, `write-tests`, `document-change`, `deploy-cloud-run`.

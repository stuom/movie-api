# AI log 03: Firestore storage layer

- **Date:** 2026-10-13
- **Goal:** Persist movies in Cloud Firestore behind the `MovieRepository` interface: create one movie atomically and read all movies, with no Firestore type leaving the `persistence` package.
- **Agents and skills used:** architect (Sonnet) for the plan review, unit-test-writer (Sonnet) for the fixture change and tests, code-reviewer for the diff, doc-writer for the docs. Skills: firestore-persistence, write-tests, document-change.

## Key prompts
1. The user's prompt asked for three storage operations: save a movie, fetch one by id, and read all movies.
2. Clarifying question from the AI: fetching by id from storage conflicts with AGENTS.md and the `firestore-persistence` skill, which allow only create and read all. The user decided to **drop fetch by id from storage**, because the in-memory `MovieCatalog` (a later slice) serves it.

## Plan proposed
- New dependency `com.google.cloud:google-cloud-firestore` (3.49.0, pinned inline in `build.gradle.kts`), approved with the plan. `compileJava` was run right after adding it, to catch `-Werror` classfile warnings early.
- `service`: `DuplicateMovieException` and `MovieStorageException`.
- `persistence`: `MovieRepository` (`create`, `findAll`), `MovieDocument` (plain field-map mapper), `FirestoreErrors` (cause-chain `ALREADY_EXISTS` check) and the thin `FirestoreMovieRepository`.
- `common/FirestoreConfig`: one `Firestore` bean from Application Default Credentials.
- Skeletons first, then failing tests, then the implementation and the gate.

The architect said "Approve with changes". All four required changes were applied:
- `MovieStorageException` goes in `service`, not `persistence`, so the later error handler never imports `persistence`.
- The cause-chain `ALREADY_EXISTS` check goes into the testable `FirestoreErrors` class.
- Every Firestore call has a bounded timeout.
- Strict checks for genres and person fields when reading a document.

Suggestions applied:
- Reuse `MovieTestData` instead of a second builder.
- A single ADR (ADR-0010) for the storage decisions.
- A broader grep for leaked types (`com.google.cloud`, `com.google.api`, `io.grpc`), which must match only `persistence/` and `common/FirestoreConfig.java`.

## Red and green
- **Red:** 34 new tests, all failing.
- **Green:** 140 tests passing, line coverage 94%. Spotless, Checkstyle and SpotBugs passed on the first run, with no exclusions (so ADR-0008 is unchanged).

## Review findings
code-reviewer found no code defects. Its "Must fix" was the missing documentation (this entry, `docs/firestore-model.md`, ADR-0010). All "Should fix" items were applied:
- Javadoc saying that a timed-out create has an unknown outcome.
- `future.cancel` on timeout.
- A `CREATED_AT` constant and a `MOVIES_COLLECTION` name.
- Extra strictness tests: actors that are not a list, an actor that is not a map, a director given as a string, a synopsis that is not a string, missing genres, an out-of-range `Long`, a whole-number `Double`.
- Test renames.
- A null-safe `MovieTestData.buildMovie`.

## Human decisions
- **No fetch by id in storage:** get by id is served by the in-memory catalog (ADR-0007), so `MovieRepository` has only `create` and `findAll`.
- Nothing was rejected or deferred.

## Outcome
- 147 tests (95% coverage) after the review fixes, all passing. `./gradlew check` is green.
- `FirestoreMovieRepository` and `FirestoreConfig` have no automated tests by design; the duplicate path is verified by the Postman collection after the first deploy.
- Commit: see git log.

## Lessons
- Check a prompt against AGENTS.md and the skills before planning: the fetch-by-id request contradicted the documented storage contract and needed a user decision.
- Put any decision that changes an HTTP status (here: duplicate vs. storage failure) into a small, unit-tested class, even inside an adapter that is otherwise untested.

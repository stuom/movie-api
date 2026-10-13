# AI log 02: Movie domain model (plain Java)

- **Date:** 2026-10-13
- **Goal:** The movie domain layer only: `Movie`, `Person`, the validation rules, text normalization and the deterministic id, as plain Java with no Spring, storage or web types.
- **Agents and skills used:** architect (Opus) for the plan review, unit-test-writer (Sonnet) for the test-data factory and tests, code-reviewer for the diff, doc-writer for the docs. Skills: write-tests, document-change.

## Key prompts
1. > Plan mode, think hard. Build the domain layer and nothing else: a movie, a person, the validation rules from the domain table in AGENTS.md, the shared text-normalisation rule, and the way a movie's identifier is derived from its name and year so that the same film can never be added twice. No Spring, no storage, no web - just plain Java that can be tested in milliseconds. Let the architect agent on opus review the plan with ultrathink, then wait for my approval. After that, ask the unit-test-writer agent on sonnet to build the test-data factory and write the tests for normalisation, identifier generation and every validation boundary before you implement anything. They should fail first. Then make them pass and run the gate.
2. Clarifying questions from the AI, raised by the architect review: add Unicode NFC to normalization? Should `MovieRules` be the only home of the body rules? The user said **yes to both**.

## Plan proposed
New classes in `movie/service`:
- `TextNormalization` (`normalize`, `trim`) and `MovieId.derivedFrom(name, year)`.
- `Person` and `Movie` records.
- `NewMovie`: raw input with a nested `PersonInput`; every field is nullable.
- `MovieRules(Clock).toValidMovie(NewMovie)`: collects every violation and throws `InvalidMovieException`, which carries a list of `FieldViolation(field, message)`.

Skeletons first, with bodies that throw `UnsupportedOperationException`. Then the tests, which must fail. Then the implementation and the gate.

The architect said "Approve with changes". All required changes were applied:
- `NewMovie` copies its lists but keeps null lists and null entries, so `MovieRules` can report them (`List.copyOf` would throw).
- One whitespace definition (`Character.isWhitespace`) for both stripping and collapsing.
- Ask the user about NFC before the golden id freezes the algorithm.
- `MovieRules` is the single source of the rules, so `createMovie(NewMovieRequest)` becomes `createMovie(NewMovie)`.
- The exact id algorithm is written down in the docs (ADR-0006).

Suggestions applied:
- The reasons for keeping `Movie` free of validation are recorded in ADR-0009.
- A new `MessageDigest` is created per call.
- Golden ids are computed outside the code, including a non-ASCII name.
- `Locale.ROOT` is proven by switching the default locale to `tr`.

## Red and green
- **Red:** 96 tests, all failing with `UnsupportedOperationException`.
- **Green:** two gate failures had to be fixed, without suppressing anything:
  - SpotBugs `EI_EXPOSE_REP` on a generic list-copy helper in `NewMovie`. Fixed by inlining `Collections.unmodifiableList(new ArrayList<>(list))` in the compact constructor.
  - javac's `serial` lint (`-Werror`) rejected a `List` field in the `Serializable` exception. `InvalidMovieException` now stores an array and returns `List.of(...)`. This deviates from the plan, which used `List.copyOf`.

## Review findings
code-reviewer had no "Must fix" items. All "Should fix" items were applied:
- A test that changes the input lists before validation.
- Null checks in `MovieTest` and `PersonTest`.
- Bounded violations for oversized lists: when `genres` or `actors` exceed their count limit, only the count is reported.
- `\u` escapes instead of invisible Unicode characters in the tests.
- Exact message assertions.
- One wording, "is required", for both null and blank values.

## Human decisions
- **NFC in normalization:** yes. Composed and decomposed "Amélie" now get the same id.
- **`MovieRules` is the only home of the body rules:** the web DTO will carry no Bean Validation constraints for them, and the service takes `NewMovie`. The service must not depend on a web DTO, and the seed loader (in the service package) needs the same rules (ADR-0009).

## Outcome
- 106 tests, all passing. Line coverage is 98% (97 of 99 lines). `./gradlew check` is green.
- Golden ids: "Aliens"/1986 -> `53e87be920fdef6e6176fa0c355406d8`, "Amélie"/2001 -> `40454ad510a87b6065b4b150928b2b65`. Both were re-checked with `printf ... | sha256sum | cut -c1-32`.
- Commit: see git log.

## Lessons
- Decide on irreversible encodings (NFC, the whitespace definition, the hash) before writing a golden test, because the test freezes them.
- With `-Xlint:all -Werror`, a `Serializable` exception cannot hold a `List` field. Use an array or a serializable copy from the start.

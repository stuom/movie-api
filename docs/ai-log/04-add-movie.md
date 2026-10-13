# AI log 04: A client can add a movie

- **Date:** 2026-10-13
- **Goal:** `POST /api/v1/movies` adds a movie and answers `201` + `Location` + the stored movie, or an RFC 7807 problem detail for every failure. This is the first end-to-end use case.
- **Agents and skills used:** architect (Opus, "ultrathink") for the plan review, unit-test-writer (Sonnet) for the tests before the implementation, code-reviewer (Opus, "think harder") for the diff, doc-writer for the docs. Skills: add-endpoint (the slice workflow), write-tests, document-change.

## Key prompts
1. The user asked for plan mode, with the contract written into `docs/api.md` **before** planning: the request body, the success response, where the new movie can be found, and each failure.
2. Then a plan covering the request and response types, the mapper, the duplicate rule, named errors and the single place that maps errors to problem details, plus OpenAPI annotations on every endpoint.
3. Workflow: the architect reviews the plan; the unit-test-writer writes the service and controller tests before the implementation; the code is built bottom-up; the code-reviewer reviews the diff; every "Must fix" gets fixed.

## Plan proposed
The contract was drafted first. Decisions in it:
- **Relative `Location`** (`/api/v1/movies/{id}`): valid per RFC 9110, and correct behind Cloud Run's proxy without trusting forwarded headers.
- **Stable response shape:** every field is always present (`null` or `[]` when unknown).
- **`about:blank` problems:** the status code says what kind of error it is.
- **No duplicate pre-check:** the atomic create with the deterministic id rejects duplicates (ADR-0006, ADR-0010). A pre-check would race with concurrent creates and read a catalog that can be up to 60 minutes stale.
- **`GET /{id}` and `MovieCatalog` deferred** to the next slice, because ADR-0007 forbids a Firestore get-by-id.
- **New dependency** springdoc-openapi 2.8.17, approved by the user together with the plan (ADR-0011).

The architect said **"Approve with changes"**. All required changes were applied:
- **R1:** scalar coercion (`"1986"` for `year`, `123` for `name`) must be a `400`.
- **R2:** the `@Schema` limits refer to the `MovieRules` constants.
- **R3:** a `ProblemViolation` DTO instead of exposing the service's `FieldViolation`.
- **R4:** `springdoc.override-with-generic-response=false` from the start.
- **R5:** the error-format details go into ADR-0004 and strict JSON into its own ADR (ADR-0012).
- **R6:** tests for `405` and `404` problem details, keys that are present with a `null` value, `"1986"`, and int overflow.

Adopted suggestions:
- a `500` detail per cause;
- the `409` detail worded from the submitted name;
- `fail-on-trailing-tokens`;
- `Location` built from the `BASE_PATH` constant;
- a Postman duplicate test;
- do not deploy slice 04 alone;
- a note that requests rejected before Spring MVC still get Boot's `/error` JSON.

## Red and green
- **Red:** 38 new tests. 35 failed against the skeletons. Three passed legitimately (`415`, `405` and `404` come from the `ResponseEntityExceptionHandler` base class).
- The test-writer found that `"name": 123` still reached the controller as `"123"` despite `allow-coercion-of-scalars=false`. The planned fallback was applied: a coercion config in `common/JacksonConfig`. Because `@WebMvcTest` leaves out user `@Configuration` classes, the controller test needs `@Import(JacksonConfig.class)`.
- **Gate fixes:**
  - Checkstyle `ConstantName`: the loggers were renamed `LOG`.
  - SpotBugs `EI_EXPOSE_REP` on the `NewMovieRequest` lists: fixed with a null-preserving copy in the compact constructor, not suppressed.

## Review findings
code-reviewer (Opus).

Must fix, both fixed:
1. **Unbounded request bodies** could exhaust the heap of the single instance. Fixed with a 256 KB `StreamReadConstraints` limit and a `413` problem detail (ADR-0012).
2. **Log injection:** `MovieService`'s info log included the client-supplied movie name. It now logs only the id and the year.

Should fix, all applied:
- "1 rule" in the singular.
- Tests for mismatches that have no field path (`[]`, a second JSON value) and for a float or boolean where text is expected.
- A number out of range is named "is out of range".
- Repeated keys are rejected (`strict-duplicate-detection`).

Nice to have, applied:
- a Javadoc caveat on the catch-all handler;
- a neutral `404` detail;
- `406` mentioned in the docs;
- a dead `@Schema` on `ProblemViolation` removed;
- a comment on the test's `@Import`.

A mutation check confirmed that the new tests bite: with the empty-path guard removed from the handler, 2 tests failed.

## Human decisions
- **Approved:** the springdoc dependency, the deferral of `GET /{id}` and the catalog, and the decision to drop the duplicate pre-check.
- **Review findings not applied:**
  - **Dropping the list copy in `NewMovieRequest`:** the copy fixes SpotBugs `EI_EXPOSE_REP`, ADR-0008 says to fix rather than suppress, and the 256 KB limit now bounds its cost.
  - **Truncating echoed unknown-field names:** already bounded by the 256 KB body limit.
  - **Handling index `-1` in `JsonFieldPath`:** Jackson does not produce such references for these bodies.
  - **Rejecting control characters in single-line fields:** this is a domain change (ADR-0006) and was raised with the user as an open question.

## Outcome
- `./gradlew check` green: 196 tests, 98% line coverage (5 of 295 lines missed). No quality gate was loosened. After the docs pass, a 406 test was added and the 413 wording lost "too deeply nested" (unreachable: Jackson rejects the first wrong token before any depth limit).
- Commit: see git log.

## Lessons
- Jackson's coercion settings are asymmetric: `allow-coercion-of-scalars=false` does not stop a number from becoming a String. Pin strictness with tests instead of trusting property names.
- A public, unauthenticated, single-instance service needs an explicit request body size limit.
- Never log client-supplied text.

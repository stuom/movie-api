# ADR-0009: Movie validation in plain Java at the service boundary

- **Status:** Accepted
- **Date:** 2026-10-13

## Context
A new movie reaches the service from two places: the `POST /api/v1/movies` body and the seed file, which `MovieSeedLoader` (in the service package) reads on startup. Both must obey the same rules from the domain table in AGENTS.md and ADR-0006. The endpoint is public and unauthenticated, so the error response must stay small even for a hostile body. The year ceiling ("current year + 5") changes over time, and movies already stored must keep loading even if a rule is tightened later.

## Options considered
1. **Bean Validation on the web DTO** (`@NotBlank`, `@Size`, ...): familiar and reported by Spring automatically, but the seed loader does not pass through the web layer, so it would need the same rules again, and the service would depend on a web DTO. "Current year + 5" needs a custom constraint.
2. **Bean Validation on both the DTO and a service type:** one source per layer, but two copies of every limit that can drift apart.
3. **One plain Java class in the service layer:** `MovieRules.toValidMovie(NewMovie)`. Testable without Spring, used by API and seed alike, and the clock is injected.

## Decision
`MovieRules` is the only home of the movie body rules. The service API becomes `createMovie(NewMovie)`; the web DTO `NewMovieRequest` will carry no Bean Validation constraints for these rules and is mapped to `NewMovie` (a record whose fields are all nullable).
- **All violations, with paths:** every broken rule is collected, in field declaration order, as a `FieldViolation(field, message)` with paths such as `name`, `genres[1]`, `actors[2].lastName`, `director.firstName`. `InvalidMovieException` carries the list and will become a `400` `ProblemDetail` (ADR-0004).
- **Bounded violations:** when `genres` or `actors` exceed their count limit, only the count violation is reported and the entries are not checked one by one, so an oversized body cannot produce an unbounded error response.
- **Clock-based ceiling:** `MovieRules` takes a `Clock`; the latest allowed year is `Year.now(clock) + 5`. Tests use a fixed clock; production will use `Clock.systemUTC()` (wired in a later slice).
- **`Movie` is not re-validated:** its constructor only requires the structural parts (`requireNonNull`, `List.copyOf`). Movies loaded from storage use the same constructor, so tightening a rule later cannot break loading movies that were valid when they were added.

## Consequences
- API input and seed data cannot disagree about what a valid movie is, and the rules are unit-tested in milliseconds (boundaries for every field).
- The client sees every problem in one response, with a field path it can act on.
- The error mapping to `ProblemDetail` is our own code instead of Spring's built-in handling of `MethodArgumentNotValidException`.
- Bean Validation may never be needed for the movie body; it may still be used for query parameters, or dropped entirely (decide when the controller slice is planned).
- A rule change applies only to new movies. Existing data that breaks a new rule has to be found and fixed by hand.

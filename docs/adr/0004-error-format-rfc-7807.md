# ADR-0004: Errors as RFC 7807 problem details

- **Status:** Accepted
- **Date:** 2026-10-12
- **Updated:** 2026-10-13 (slice 04: problem members and extensions, `ApiExceptionHandler`, unreadable bodies, `413`, `500` detail per cause); 2026-10-14 (slice 08: query violations)

## Context
Clients need consistent, machine-readable errors that never leak internals.

## Options considered
1. Ad-hoc JSON error objects per endpoint.
2. RFC 7807 `application/problem+json`, supported natively by Spring through `ProblemDetail`.

## Decision
All errors are returned as `ProblemDetail` by one `@RestControllerAdvice`: `400` for validation errors and invalid search parameters, `404` for unknown ids, `409` for duplicates, `500` for failures (details only in logs).

### Details (slice 04)
- **One handler:** `common/ApiExceptionHandler` extends `ResponseEntityExceptionHandler`, so Spring MVC's own errors (unknown path `404`, wrong method `405`, `406`, wrong content type `415`, missing body) are problem details too. It imports `movie.service` exceptions only, never `persistence` (ADR-0010).
- **Members:** `type` stays `about:blank` and `title` is the HTTP status phrase. `instance` is the request path. `detail` is always written by the handler and never copied from an exception message, so class names, Jackson messages and storage errors cannot leak.
- **Extensions:**
  - `violations`: a list of `{field, message}` on `400` for a request body. It uses the API's own `common/ProblemViolation` record, so the service's `FieldViolation` never becomes part of the API.
  - Query parameters (slice 07, extended in slice 08): `InvalidQueryException` becomes `400` with the fixed detail "The query parameters are invalid; see violations." and one violation per problem, `field` being the parameter name. Combining search fields gives one violation per search parameter received, so `field` is always a real parameter name and the fields never have to be parsed out of the `detail` (ADR-0007).
  - `movieId`: on `409`, the id of the movie that already exists.
- **Broken rules:** `InvalidMovieException` becomes `400` with every violation and a counted detail ("breaks 1 rule", "breaks 3 rules").
- **Unreadable bodies:** these become `400`. When Jackson knows the location, there is one violation whose path is rendered by `JsonFieldPath` in the same format as `MovieRules`. Its message is "is not a known field", "has the wrong type" or "is out of range". When there is no location (invalid JSON, not an object, repeated key, trailing content, missing body), `violations` is left out.
- **Oversized bodies:** a body over the size limit (ADR-0012) becomes `413 Payload Too Large`, so the client knows that fixing a field will not help.
- **Unknown path:** the `404` detail is a neutral "There is no endpoint at this path." instead of Spring's wording about static resources.
- **Server errors (`500`) get a detail per cause:**
  - `MovieStorageException`: the detail says the movie may have been saved and retrying is safe, because a timed-out create has an unknown outcome and a retry answers `409` (ADR-0010).
  - Any other exception: a generic detail.
  - Both are logged at error level with the stack trace. Client errors (`4xx`) are not logged as errors.

## Consequences
- One documented error shape for every endpoint, covered by tests that assert the body, not only the status code.
- The error shape is documented in `docs/api.md` and, as examples, in the OpenAPI annotations (ADR-0011).
- The catch-all `@ExceptionHandler(Exception.class)` runs before Spring resolves `@ResponseStatus` on exceptions. Every new exception that should not become a `500` (for example the future `MovieNotFoundException`) therefore needs its own handler method.
- Requests that Tomcat rejects before they reach Spring MVC (for example a malformed request line) still get Spring Boot's default `/error` JSON, not a problem detail.

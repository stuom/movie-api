# ADR-0004: Errors as RFC 7807 problem details

- **Status:** Accepted
- **Date:** 2026-10-12

## Context
Clients need consistent, machine-readable errors that never leak internals.

## Options considered
1. Ad-hoc JSON error objects per endpoint.
2. RFC 7807 `application/problem+json`, supported natively by Spring through `ProblemDetail`.

## Decision
All errors are returned as `ProblemDetail` by one `@RestControllerAdvice`: `400` for validation errors and invalid search parameters, `404` for unknown ids, `409` for duplicates, `500` with a generic message for anything unexpected (details only in logs).

## Consequences
- One documented error shape for every endpoint, covered by tests that assert the body, not only the status code.
- Validation errors list offending fields in an extension member; the shape is documented in `docs/api.md`.

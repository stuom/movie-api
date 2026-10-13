# ADR-0002: One endpoint for list and search

- **Status:** Accepted, amended by [ADR-0007](0007-in-memory-search-over-firestore-catalog.md) (pagination and filters)
- **Date:** 2026-10-12
- **Updated:** 2026-10-13 (originally cursor pagination with three filters; now page/size with all-field filters)

## Context
The API must list movies and search them by any field. Originally the Firestore cost model favoured cursor pagination; with in-memory search (ADR-0007) that constraint is gone.

## Options considered
1. **Separate `/movies/search` endpoint.** Two near-identical contracts to document and test.
2. **Filters on the collection** (`GET /movies?name=` or `?minYear=&maxYear=`, `?genre=`, `?viewerAge=`, `?minRating=&maxRating=`, `?director=`, `?actor=`, `?synopsis=`): the REST-idiomatic way to filter a resource collection; list is simply search with no filter.
3. **Cursor pagination (`pageToken`)**: needed when the database bills per skipped document. Not needed with an in-memory catalog.
4. **Offset pagination (`page`, `size`)**: familiar, supports total counts and random access, trivial over an in-memory list.
5. **Summary items in the list, full details only on `GET /movies/{id}`:** smaller payloads, but two response types. A movie is about 1 KB, so not worth it yet.

## Decision
`GET /api/v1/movies` with an optional search on one field at a time (every field is searchable, semantics in ADR-0007; combining fields is rejected with `400`), and `page` (0-based) / `size` (default 20, max 100) pagination. The response is `{ items, page, size, totalItems, totalPages }`, ordered by name then id. Items contain all movie fields.

## Consequences
- One contract serves list and search; clients can jump to any page and know the total.
- Searches cannot be combined. Allowing combinations later is backward compatible (loosen the validation and AND the predicates), so nothing is lost by starting simple.
- Fixed sort order; a `sort` parameter can be added later because sorting is in memory.
- If movies grow large, introduce a summary type for list items and supersede this ADR.

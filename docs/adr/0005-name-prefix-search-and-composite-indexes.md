# ADR-0005: Name prefix search and composite indexes

> Superseded: the requirement grew to searching every field (one field per request), including substring and multi-word matching that Firestore queries cannot do. Kept for history.

- **Status:** Superseded by [ADR-0007](0007-in-memory-search-over-firestore-catalog.md)
- **Date:** 2026-10-12
- **Updated:** 2026-10-13 (field names and genre list follow the provided dataset, see ADR-0006)

## Context
Users expect to search movies by name, year and genre. Firestore cannot do case-insensitive or substring (`contains`) matching; it supports equality, range and `array-contains` queries on indexed fields. A movie has several genres (a list), and Firestore allows only one `array-contains` clause per query.

## Options considered
1. **Substring search by reading all documents and filtering in memory:** unbounded reads, exhausts the free quota, does not scale.
2. **Prefix search on a normalized lowercase field** (`nameLower >= q AND nameLower < q + '\uf8ff'`): cheap, bounded, index-backed. Matches only the start of the name.
3. **Token array (`array-contains` on words of the name):** matches any word, but uses the single `array-contains` slot needed for genres and complicates ordering and the write path.
4. **External search service (Algolia, Elasticsearch):** powerful, but adds cost and operations far beyond this project.

## Decision
- `name` is a **case-insensitive prefix match** on `nameLower`.
- `genre` matches **one** genre exactly (case-insensitive) with `array-contains` on `genresLower`; only one genre per request.
- `year` is an exact match on `year`.
- Results are ordered by `nameLower`, then document id.
- `ageLimit`, `rating`, actors, director and synopsis are returned but not filterable.
- Composite indexes: `(year, nameLower)`, `(genresLower array-contains, nameLower)` and `(year, genresLower array-contains, nameLower)`, created with `gcloud` and documented in `docs/firestore-model.md`.

## Consequences
- "ali" finds "Aliens" and "Alien" but "lien" does not. This is documented in the API contract so the behavior is not a surprise.
- Filtering by several genres at once (any/all) is not supported. It would need a different data layout and a new ADR.
- Every new filter or sort combination needs a new composite index and a documented decision.
- Missing indexes surface as errors only on the real database, so each query shape gets a post-deploy smoke test.
- Full-text search (including actors or synopsis) can be added later behind the same `MovieRepository` interface.

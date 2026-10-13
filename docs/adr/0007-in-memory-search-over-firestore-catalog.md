# ADR-0007: In-memory search over a Firestore-backed catalog

- **Status:** Accepted
- **Date:** 2026-10-13
- **Updated:** 2026-10-13 (`year` became `minYear` / `maxYear`; searches use one field at a time, not combinations; get by id reads from the catalog)
- **Supersedes:** ADR-0005. **Amends:** ADR-0002 (pagination and filters), ADR-0003 (how Firestore is used), ADR-0006 (stored fields).

## Context
Every movie field must be searchable, **one field per request** (no combinations such as "director is X and actor is Y"):
- `name`;
- year range: `minYear` and/or `maxYear`;
- `genre`;
- `viewerAge`: all movies the viewer is old enough for (`ageLimit <= viewerAge`);
- rating range: `minRating` and/or `maxRating`;
- `director` and `actor`, matching both first name and last name;
- words inside the `synopsis`.

Single-field searches would be easy for some fields in Firestore: ranges on year, rating and age limit, `array-contains` on genre and a prefix match on name all work with Firestore's automatic single-field indexes. But Firestore has no substring or full-text matching, and one `array-contains` matches one exact value. So these searches cannot be done natively: name *contains*, several words in a first or last name, and several words in the synopsis.

## Options considered
1. **Firestore queries with stored helper fields (lowercase copies, word arrays):** covers ranges and genre, but not substring or multi-word matching, and adds derived fields that must be kept in sync. Rejected.
2. **Firestore queries for the fields it supports, in-memory matching for the rest:** two search mechanisms with different behavior and cost, harder to understand and test. Rejected.
3. **Firestore as durable store, all search in memory over a cached catalog (chosen):** one simple mechanism for every field. All search logic is plain Java, so it is fully unit-testable and needs no indexes, helper fields or emulator. Cost and scale are bounded by the catalog size.
4. **External search service (Algolia, Elasticsearch, Typesense):** powerful, but adds cost and operations beyond this project.
5. **Relational database with SQL/full-text search (Cloud SQL, or an external free-tier Postgres):** the right choice for a large catalog. Cloud SQL is not free and an external database adds a second vendor. This is the documented upgrade path.

## Decision
- Firestore remains the durable store. Allowed operations are: create one movie, read all movies. No filtered Firestore queries, no composite indexes, no derived fields.
- `GET /movies/{id}` also reads from the catalog snapshot, not from Firestore. Lookups then cost no reads and behave the same as search: a movie is either visible to both or to neither.
- `MovieCatalog` keeps an immutable in-memory snapshot of all movies. It loads at startup, is replaced after every successful create, and reloads when older than `app.catalog.refresh-interval` (default 60 minutes).
- `MovieSearch` (plain Java in the service layer) applies the search, sorts by name then id, and paginates.
- **One search field per request.** Sending two or more different search fields is a `400` that names them; they are never combined or silently ignored. `minYear` + `maxYear` count together as one field (year) and `minRating` + `maxRating` count together as one field (rating). `page` and `size` are not search fields. No search field means list everything.
- Search semantics (the contract, also in AGENTS.md and `docs/api.md`):
  - `name`: case-insensitive substring.
  - `minYear`, `maxYear`: inclusive bounds on the release year, either or both, any integers; `minYear > maxYear` is a `400`. For one exact year set both to the same value. A year outside the data simply matches nothing.
  - `genre`: case-insensitive exact match against any of the movie's genres.
  - `viewerAge`: `ageLimit <= viewerAge` (inclusive). Range 0-120.
  - `minRating`, `maxRating`: inclusive bounds, either or both, integers 1-5; `minRating > maxRating` is a `400`.
  - `director`: the director's first name or last name contains every word of the text (case-insensitive).
  - `actor`: some actor's first name or last name contains every word of the text; the words must match the same actor.
  - `synopsis`: contains every word of the text, in any order.
  - Blank text parameters count as not provided. Accents are not folded.
- Pagination is `page` (0-based) and `size` (default 20, max 100); the response has `totalItems` and `totalPages`.
- Cloud Run stays at `--max-instances 1` so there is a single catalog.

## Consequences
- Every catalog load reads every movie document (one read each). With a 50,000 reads/day free quota, the catalog should stay under about 1,000 movies; with the default refresh interval that is roughly 30,000 reads a day in the worst case (24 refreshes plus a few cold starts).
- Cold starts load the whole catalog, which is fast for a small catalog.
- A second instance (or a manual edit in the Firestore console) can show stale data for up to one refresh interval. This applies to get by id as well as search.
- Search is a linear scan, which is instant for hundreds or thousands of movies and unsuitable for millions.
- Relevance ranking, typo tolerance and accent folding are not provided.
- Rejecting combined searches keeps the contract and tests small. Allowing combinations later is backward compatible (loosen the validation and AND the predicates).
- To outgrow this design, replace `MovieCatalog`/`MovieSearch` with SQL or a search service behind the same service interface and write a new ADR.

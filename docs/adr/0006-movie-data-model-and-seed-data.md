# ADR-0006: Movie data model and seed data

- **Status:** Accepted
- **Date:** 2026-10-13
- **Updated:** 2026-10-13 (all fields are searchable, see ADR-0007; no extra search fields are stored)

## Context
The user provided a sample dataset (`movies-compact.json`, 20 movies) that defines the shape of a movie: `name`, `year`, a list of `genres`, `ageLimit`, `rating`, a `director` (first and last name), a list of `actors` (first and last name) and a `synopsis`. The dataset is also the natural content for a demo deployment. It contains typos and likely wrong values (for example the genre "Familty") and stray whitespace in names.

## Options considered
1. **Normalize into collections** (movies, people, genres) with references: avoids duplication, but every movie read needs several more reads, which costs free-tier quota, and relations are awkward in Firestore.
2. **Embed everything in the movie document:** one read per movie, simple to map, some duplication of person names (acceptable at this scale).
3. **Keep the dataset's vocabulary** (`name`, `year`) or rename to `title`, `releaseYear`: keeping it means the sample file doubles as an example request body.
4. **Genres as a fixed enum vs free text:** an enum would reject the typo "Familty" but needs a maintained vocabulary and a migration story. Free text is simpler for now.
5. **Seed directly into the database vs through the service:** going through `MovieService.createMovie` gives seed data the same validation, trimming and duplicate detection as API input.

## Decision
- A movie is one Firestore document with embedded `director` (a `Person`), `actors` (list of `Person`) and `genres` (list of strings), using the dataset's field names. No derived search fields are stored; text is normalized in memory when searching (ADR-0007).
- Validation: `name` 1-200 chars, `year` 1888..current year + 5, `genres` 1-5 entries of 1-50 chars, `ageLimit` 0-18, `rating` 1-5, `actors` at most 30, `synopsis` at most 2000, person names 1-100 chars. All text is trimmed.
- Genres are free text for now.
- The document id is a deterministic hash of normalized `name` + `year`, which also enforces uniqueness (ADR-0003).
- Sample data is stored at `src/main/resources/seed/movies.json` and loaded by `MovieSeedLoader` on startup when `app.seed.enabled=true` and the loaded catalog is empty. Entries go through `MovieService.createMovie`; invalid or duplicate entries are logged and skipped.
- Suspected mistakes in the dataset are reported to the user, not silently corrected.

## Consequences
- Reading or listing movies never needs joins; the cost is one read per movie.
- The same person appearing in several movies is stored several times; renaming a person means updating every movie. Acceptable for this scope.
- Free-text genres allow inconsistent spellings ("Sci-fi" vs "Science Fiction"). A controlled vocabulary or a genre endpoint can be added later with a new ADR.
- Actors, director and synopsis are searched in memory (ADR-0007), so adding a filter on any field needs no schema or index change.
- The seed file doubles as an example of a valid request body and as test material.

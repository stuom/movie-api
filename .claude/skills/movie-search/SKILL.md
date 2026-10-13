---
name: movie-search
description: Search semantics and implementation rules for the Movie API - one search field per request (name, minYear/maxYear, genre, viewerAge, minRating/maxRating, director, actor, synopsis), text matching, sorting, paging, validation and the unit-test matrix. Use whenever adding, changing, testing or documenting a search filter or the list endpoint, or when the user mentions search, filter, viewer age, rating or year range, actors, director or synopsis.
---

# Movie search

Search runs in memory over the `MovieCatalog` snapshot (ADR-0007), as plain Java in the service layer.
A request uses **at most one search field**. Filters are never combined, which keeps the contract and the code simple.
Because search is plain Java it can be tested exhaustively with fast unit tests, which is why it carries most of this project's test weight.

## The one-field rule
- Search fields are: `name`, `year` (`minYear` and/or `maxYear`), `genre`, `viewerAge`, `rating` (`minRating` and/or `maxRating`), `director`, `actor`, `synopsis`.
- `minYear` + `maxYear` together are **one** field (year); `minRating` + `maxRating` together are **one** field (rating).
- `page` and `size` are not search fields.
- Blank text parameters are treated as not provided, so they do not count.
- Zero fields = list everything. More than one field = `400` (`InvalidSearchCriteriaException`) whose detail names the fields received, for example "Use only one search field at a time; received: actor, director". Never silently ignore extra fields.

## Classes
- `MovieSearchCriteria`: a **sealed interface** with one record per field plus `All`, for example `NameContains(text)`, `YearRange(min, max)`, `Genre(text)`, `ViewerAge(age)`, `RatingRange(min, max)`, `DirectorNamed(words)`, `ActorNamed(words)`, `SynopsisContains(words)`.
  A static factory `MovieSearchCriteria.of(...)` takes all the raw request values, normalizes text, enforces the one-field rule and validates ranges. The sealed type means `MovieSearch` uses an exhaustive `switch` (Java 21 pattern matching), so adding a filter fails to compile until it is handled.
- `MovieSearch`: `search(List<Movie>, MovieSearchCriteria, int page, int size)` returns a `MoviePage`. One small, named, testable method per filter.
- `MovieService.searchMovies(criteria, page, size)` takes the snapshot from `MovieCatalog` and calls `MovieSearch`.
- The controller maps request parameters to `MovieSearchCriteria.of(...)` and the `MoviePage` to `MoviePageResponse`. No matching logic in the controller.

## Semantics (the contract; keep AGENTS.md, ADR-0007 and `docs/api.md` identical to this)
| Field / parameters | Rule |
|---|---|
| `name` | `normalize(movie.name)` contains `normalize(text)` |
| `minYear` / `maxYear` | `movie.year >= minYear` and/or `movie.year <= maxYear`, inclusive; equal values = one exact year; `minYear > maxYear` is invalid; no other range restriction |
| `genre` | any genre with `normalize(genre) == normalize(text)` |
| `viewerAge` (0-120) | `movie.ageLimit <= viewerAge` (inclusive) |
| `minRating` / `maxRating` (1-5) | `movie.rating >= minRating` and/or `movie.rating <= maxRating`, inclusive; `minRating > maxRating` is invalid |
| `director` | movie has a director and **every word** of the text is contained in the normalized first name or last name of that director |
| `actor` | **some** actor satisfies the same person rule; all words must match the same actor |
| `synopsis` | movie has a synopsis and **every word** of the text is contained in it (any order) |
| `page` / `size` | `page >= 0` (default 0), `1 <= size <= 100` (default 20) |

- Person rule detail: a word matches a person if `normalize(firstName)` or `normalize(lastName)` contains it. "ryan", "gosling" and "ryan gosling" all find Ryan Gosling. For `actor`, "ryan wright" finds nothing in a movie whose cast has Ryan Gosling and Robin Wright, because the words must match the same person.
- `normalize` = `TextNormalization.normalize`: Unicode NFC, trim, collapse inner whitespace, lowercase with `Locale.ROOT`. A word is a whitespace-separated part of the normalized text (split on the same `Character.isWhitespace` definition). Accents are not folded.
- A movie missing the field being searched (no director, no synopsis, no actors) never matches that search.
- Sorting: `normalize(name)` ascending, then `id`. Response: `items`, `page`, `size`, `totalItems`, `totalPages` (`ceil(totalItems / size)`, 0 when there are no items). A page beyond the last returns empty `items` with the true totals, not an error.

## Adding or changing a search field
1. Update the semantics table here, in AGENTS.md (API conventions), in `docs/api.md` and in ADR-0007 (or a new ADR if the behavior is a decision).
2. Add a record to `MovieSearchCriteria`, extend `MovieSearchCriteria.of(...)` (including the one-field rule and validation), add its method and `switch` case in `MovieSearch`, and add the request parameter and OpenAPI annotation in the controller.
3. Write the tests below before the implementation (see the `write-tests` skill).
4. Do not touch Firestore: no new queries, indexes or stored fields are needed.

## Unit-test matrix (`MovieSearchTest` and `MovieSearchCriteriaTest`)
Use a small fixed list of movies built with `MovieTestData`, ideally a handful taken from the real sample data.
- Each field on its own: matching, non-matching, case-insensitive, extra spaces.
- `name`: substring in the middle and at the end, not only prefix.
- Years: only `minYear`, only `maxYear`, both, equal bounds (exact year), bounds inclusive, `minYear > maxYear` rejected, a range outside the data returns an empty page.
- `viewerAge`: equal to the limit is included, one below is excluded, `0` returns only movies with limit 0, values outside 0-120 rejected.
- Ratings: only `minRating`, only `maxRating`, both, equal bounds, bounds inclusive, `min > max` rejected, values outside 1-5 rejected.
- `director` and `actor`: first name only, last name only, both words, words in either order, multi-word last name ("lee jones", "del toro"), words matching different actors must not match, movie without director/actors does not match.
- `synopsis`: single word, several words in any order, a word missing -> no match, movie without synopsis.
- **One-field rule** (`MovieSearchCriteriaTest`): no fields -> `All`; each field alone builds the right criteria; the two year bounds count as one field and the two rating bounds count as one field; any two different fields (for example `actor` + `director`, `minYear` + `minRating`) are rejected, and the error names the fields received; blank text plus one real field is accepted.
- Paging and order: ordering by name then id, `page`/`size` slicing, page beyond the end, `totalItems`/`totalPages`, size 100 accepted and 101 rejected, negative page rejected.

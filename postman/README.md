# Postman API tests

API tests for the deployed Movie API, run by hand in Postman (or with Newman). The contract they
test is [`docs/api.md`](../docs/api.md). They are not part of `./gradlew check`.

> **Data warning:** every run creates one extra movie named `0 Pmtest <runId>` in the **live**
> Firestore database, and the API has no way to delete it (there is no delete endpoint, by design).
> Remove leftovers **by hand in the Firestore console** (collection of movies, documents whose
> `name` starts with `0 Pmtest`). If a test of a 400 or 413 request fails, that request may have stored a
> movie whose name starts with `0 Pmtest`; delete it the same way. Ask before running the collection against the deployed service.

## Files
- `movie-api.postman_collection.json`: the requests and their test scripts (Postman Collection v2.1).
- `movie-api.postman_environment.json`: the environment with the single variable `baseUrl`.

## Setup
1. Get the service URL:
   ```bash
   gcloud run services describe movie-api --region europe-north1 --project movie-api-511411 --format 'value(status.url)'
   ```
2. In Postman, **Import** both JSON files.
3. Select the environment `Movie API (Cloud Run)`. Its `baseUrl` is already the live service,
   `https://movie-api-719585522889.europe-north1.run.app`; change it only if step 1 prints another URL. No trailing slash.

## Run
Run the whole collection, in order, with the **Collection Runner** (later requests use variables set
by earlier ones). Or from the repository root:
```bash
npx newman run postman/movie-api.postman_collection.json -e postman/movie-api.postman_environment.json
```
Add `--env-var baseUrl=https://<other-service-url>` to run against another deployment.
No request asserts response times: a Cloud Run cold start can take several seconds.

## What each folder checks
The folders run in this order; later requests use the collection variables set by earlier ones
(`runId`, `movieId`, `movieLocation`, `createdMovie`, `listTotalItems`, `listItemIds`). The first
request clears them all, so a run never passes on a previous run's values.

### 01 Add movie (`POST /api/v1/movies`, `GET /api/v1/movies/{id}`)
| Request | Checks |
|---|---|
| `POST add a movie - 201 with trimmed fields` | Sets a fresh `runId`. `201`, JSON content type, a 32-hex `id`, `Location` `/api/v1/movies/{id}` ending with the id, every field equal to the trimmed input (name, a genre, the director's last name and the synopsis are sent padded with spaces), `director`/`actors`/`synopsis` present. Saves `movieId`, `movieLocation` and the body as `createdMovie`. **Stores the run's one test movie.** |
| `GET the added movie by its Location - 200 with the same body` | Follows the saved `Location`: `200`, JSON, `id` equal to `movieId`, and the body deep-equal to the `201` body. |
| `POST add the same movie again - 409 with movieId` | Same year, name in different case with extra spaces: `409` problem detail (`type` `about:blank`, `title` `Conflict`) whose `movieId` is the saved id. Exercises the real Firestore duplicate path. |
| `POST reject an invalid movie - 400 with violations` | Blank name, rating 9, actor without `lastName`: `400` problem detail, `detail` counting 3 rules, `violations` exactly `name` / `rating` / `actors[0].lastName` in field order. |
| `POST reject an unknown field - 400 naming synopsys` | Valid movie plus `synopsys`: `400` problem detail with the violation `{"field": "synopsys", "message": "is not a known field"}`. Uses the name `0 Pmtest unknown <runId>`, so a regression that stored it would be easy to spot and delete. |
| `POST invalid movie, <case> - 400 with violations` (10 requests) | One request per broken rule, otherwise valid, all sharing the recognizable name `0 Pmtest invalid <runId>` (the missing-name and blank-name requests have no usable name) and the genre `Pminvalid<runId>`: missing name, blank name, year 1800, year 3000, empty genres, rating 0, rating 6, ageLimit 19, ageLimit -1, an actor with a blank lastName. Each is a `400` problem detail with exactly one violation, on the right field. |
| `POST invalid movie, malformed JSON - 400 problem` | Truncated JSON: `400` problem detail. |
| `POST movie with a client-supplied id` / `wrong type for year` | `400` with `id` `is not a known field`, and `year` `has the wrong type`. |
| `POST movie larger than 256 KB - 413 problem` | The pre-request script pads the synopsis to 300,000 characters: `413` problem detail mentioning 256 KB. Nothing is stored. |
| `GET a movie with an unknown id - 404 problem` | `GET /api/v1/movies/does-not-exist-<runId>`: `404` problem detail (`title` `Not Found`, `instance` the request path), `detail` exactly `There is no movie with this id.` and not repeating the id. |

### 02 List movies (`GET /api/v1/movies` without search fields)
Every `200` request checks: JSON content type; `items` (array), `page`, `size`, `totalItems` (at
least 1) and `totalPages` = `ceil(totalItems / size)`; the number of items for that page
(`min(size, totalItems - page * size)`, never negative); every item has every movie field; and the
order: names compared ignoring case (normalized like the server: NFC, whitespace collapsed, trimmed,
lowercase) with `<`, not `localeCompare`, because the server compares UTF-16 code units, then `id`.

| Request | Checks |
|---|---|
| `GET list movies with defaults - 200 page 0 size 20 sorted by name` | `page` 0, `size` 20, at most 20 items; the run's test movie is on page 0 (its name starts with `0`) with the same body as when it was added. Saves `listTotalItems` and the item ids. |
| `GET list movies page=0&size=2 - first 2 movies` | `size` 2, at most 2 items, the same `totalItems`, and the items are the first two of the default list. |
| `GET list movies page=1&size=2 - movies 3 and 4` | `page` 1, the items are positions 2 and 3 of the default list (empty when the catalog has fewer than 3 movies). |
| `GET list movies size=100 - 200 accepted` | The largest page size is accepted, not clamped. |
| `GET list movies page=9999&size=2 - 200 empty items with true totals` | A page past the end: `200`, `items` `[]`, the same `totalItems` and the matching `totalPages`. |
| `GET list movies size=101`, `size=0`, `page=-1`, `page=abc`, `page=1&page=2`, `pgae=2` | Each `400` problem detail (`title` `Bad Request`, `instance` `/api/v1/movies`) whose `violations` are exactly the one documented entry: `size` `must be between 1 and 100`, `page` `must be 0 or more`, `page` `must be a whole number`, `page` `must be given only once`, `pgae` `is not a known parameter`. |
| `GET list movies page=-1&size=101&pgae=2 - 400 naming every problem in order` | The example from `docs/api.md`: all three violations, in the fixed order `page`, `size`, unknown parameters. |

The list checks rely only on the run's own test movie, not on the sample data or on how many movies
exist. Two conditions are assumed: no one adds a movie while the folder runs (the paging requests
compare against the default list), and fewer than 20 names sort before `0 Pmtest <runId>` (so it stays on page 0). Old
`0 Pmtest` leftovers sort before the new one, so remove them now and then.

### 03 Search (`GET /api/v1/movies` with one search field)
Every search searches for the run's own test movie, so no assertion depends on the sample data. The
test movie's values are all built from `runId`: name `0 Pmtest <runId>`, year 1900, genres
`Pmgenre<runId>` and `Drama`, age limit 18, rating 1, director `Pmdir<runId> Lastdir<runId>`, actors
`Pmact<runId> Lastact<runId>` and `Other<runId> Person<runId>`, synopsis `pmsyn<runId> alpha beta`.

Every `200` search uses `size=100` (except the paging requests) and checks: JSON content type; the
page shape (`page`, `size`, `totalItems`, `totalPages` = `ceil(totalItems / size)`); the number of
items for that page, so `totalItems` counts the matches; the order (name ignoring case, then `id`);
that **every** returned item satisfies the search rule (checked with the same normalization as the
server: NFC, whitespace collapsed, trimmed, lowercase); and that the test movie is (positive cases)
or is not (negative cases) among the items. Requests marked "upper case" set a request-local
variable `runIdUpper` in their pre-request script.

| Field | Requests (one search field each) |
|---|---|
| `name` (substring, case-insensitive) | `pmtest <runId>`: exactly the test movie (`totalItems` 1, same body as when added; also proves the rejected and duplicate requests of folder 01 stored nothing); the same in upper case; only `<runId>` (a substring that is not a prefix); a name nothing contains: `items` `[]`, `totalItems` 0, `totalPages` 0. |
| year (`minYear` / `maxYear`, inclusive) | Both 1900 (exact year); only `minYear=1900`; only `maxYear=1900`; `minYear=1901` and `maxYear=1899` exclude the test movie. |
| `genre` (equal, case-insensitive, any genre) | The unique genre (exactly the test movie); the same in upper case; the second genre `drama`; `Pmgenre` (only part of the genre) excludes it; an unknown genre: empty. |
| `viewerAge` (`ageLimit <= viewerAge`) | 18 includes it (equal to the limit); 17 excludes it; 0 returns only age limit 0. |
| rating (`minRating` / `maxRating`, inclusive) | `maxRating=1` includes it; `minRating=2` excludes it; `minRating=1&maxRating=1` includes it, every item rated 1; `minRating=5` returns only rating 5. |
| `director` (every word in the first or last name) | First name; last name (lower case); both words; both reversed in mixed case with extra spaces. Excluded: a director word plus an actor's last name; an actor's name searched as director. |
| `actor` (every word in the first or last name of the **same** actor) | First name; last name (upper case); both words of actor 1; both words of actor 2, reversed. Excluded: first name of actor 1 plus last name of actor 2; the director's name searched as actor. |
| `synopsis` (every word, any order, substring) | The unique word (upper case); `beta pmsyn<runId>` (reverse order); `pmsyn<runId> alph` (part of a word); `pmsyn<runId> gamma` (a missing word) excludes it. |
| blank text | `genre=%20&actor=Pmact<runId>`: the blank genre counts as not given, so this is an actor search (`200`, includes the test movie). |
| paging | `genre=Pmgenre<runId>&page=0&size=1`: the one match, `totalItems` 1; `page=1&size=1`: `items` `[]`, `totalItems` and `totalPages` still 1. |

Errors: each a `400` problem detail (`title` `Bad Request`, `instance` `/api/v1/movies`) with the
`detail` `The query parameters are invalid; see violations.` and **exactly** these `violations`, in
order:

| Request | `violations` |
|---|---|
| `actor=...&director=...` | `director`, then `actor`, each `must not be combined with another search field; received: director, actor` (table order, not request order). |
| `minYear=1900&minRating=1` | `minYear`, then `minRating`, each `... received: minYear, minRating`. |
| `minYear=2010&maxYear=2000&minRating=9` (the `docs/api.md` example) | `minRating` `must be between 1 and 5`; `minYear` `must not be greater than maxYear`; then `minYear`, `maxYear`, `minRating` each `... received: minYear + maxYear, minRating`. |
| `minYear=2010&maxYear=2000` | `minYear` `must not be greater than maxYear`. |
| `minRating=5&maxRating=1` | `minRating` `must not be greater than maxRating`. |
| `minRating=6`, `maxRating=0` | The parameter, `must be between 1 and 5`. |
| `viewerAge=121`, `viewerAge=-1` | `viewerAge` `must be between 0 and 120`. |
| `viewerAge=abc` | `viewerAge` `must be a whole number`. |
| `genre=drama&genre=drama` | `genre` `must be given only once`. |
| `name=pmtest&utm_source=x` | `utm_source` `is not a known parameter`. |

The broad searches (`minYear=1900`, `viewerAge=18`, `maxRating=1`, `genre=drama`, ...) expect the
test movie on their first page of 100. It sorts near the top (its name starts with `0`), so this
holds while fewer than 100 names sort before it; remove old `0 Pmtest` leftovers now and then.

## Checks on every request
Every request also runs a collection-level check: an error response (4xx/5xx) must not contain
exception names or stack-trace lines.

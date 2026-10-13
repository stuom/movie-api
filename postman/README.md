# Postman API tests

API tests for the deployed Movie API, run by hand in Postman (or with Newman). The contract they
test is [`docs/api.md`](../docs/api.md). They are not part of `./gradlew check`.

> **Data warning:** each run adds exactly one movie named `0 Pmtest <runId>` to the **real**
> database. There is no delete endpoint, so remove leftovers by hand in the Firestore console
> (collection of movies, documents whose `name` starts with `0 Pmtest`). Ask before running the
> collection against the deployed service.

## Files
- `movie-api.postman_collection.json`: the requests and their test scripts (Postman Collection v2.1).
- `movie-api.postman_environment.json`: the environment with the single variable `baseUrl`.

## Setup
1. Get the service URL:
   ```bash
   gcloud run services describe movie-api --region us-central1 --format 'value(status.url)'
   ```
2. In Postman, **Import** both JSON files.
3. Select the environment `Movie API (Cloud Run)` and set `baseUrl` to the URL from step 1, replacing
   `https://REPLACE-WITH-CLOUD-RUN-URL`. No trailing slash.

## Run
Run the whole collection, in order, with the **Collection Runner** (later requests use variables set
by earlier ones). Or from the repository root:
```bash
npx newman run postman/movie-api.postman_collection.json -e postman/movie-api.postman_environment.json --env-var baseUrl=https://<your-service-url>
```
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

Every request also runs a collection-level check: an error response (4xx/5xx) must not contain
exception names or stack-trace lines.

Search fields are not implemented yet; until then they are unknown parameters (`400`). The search
folder will be added with them.

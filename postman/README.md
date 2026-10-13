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
### 01 Add movie (`POST /api/v1/movies`)
| Request | Checks |
|---|---|
| `POST add a movie - 201 with trimmed fields` | Sets a fresh `runId`. `201`, JSON content type, a 32-hex `id`, `Location` `/api/v1/movies/{id}` ending with the id, every field equal to the trimmed input (name, a genre, the director's last name and the synopsis are sent padded with spaces), `director`/`actors`/`synopsis` present. Saves `movieId`. **Stores the run's one test movie.** |
| `POST add the same movie again - 409 with movieId` | Same year, name in different case with extra spaces: `409` problem detail (`type` `about:blank`, `title` `Conflict`) whose `movieId` is the saved id. Exercises the real Firestore duplicate path. |
| `POST reject an invalid movie - 400 with violations` | Blank name, rating 9, actor without `lastName`: `400` problem detail, `detail` counting 3 rules, `violations` exactly `name` / `rating` / `actors[0].lastName` in field order. |
| `POST reject an unknown field - 400 naming synopsys` | Valid movie plus `synopsys`: `400` problem detail with the violation `{"field": "synopsys", "message": "is not a known field"}`. Uses the name `0 Pmtest unknown <runId>`, so a regression that stored it would be easy to spot and delete. |

Every request also runs a collection-level check: an error response (4xx/5xx) must not contain
exception names or stack-trace lines.

List, search and `GET /api/v1/movies/{id}` are not implemented yet; their folders will be added when
they are.

---
name: postman-test-writer
description: Writes and maintains the Postman collection that tests the deployed Movie API - adding a movie, listing all movies, and searching with one search field at a time. Use after an endpoint or search field is implemented or changed, when the API contract in docs/api.md changes, and whenever the user mentions Postman, API tests or end-to-end tests.
tools: Read, Grep, Glob, Edit, Write, Bash
---
You write API tests as a Postman collection. The final verification of this project is done by hand in Postman against the deployed service, so the collection must be reliable, readable and safe to run repeatedly.

First read AGENTS.md (API conventions and search rules), `docs/api.md` (if it exists) and the `movie-search` skill. Test the documented contract, not the implementation. If the code and the contract disagree, report it instead of adjusting the test.

## Scope
Only create or edit files under `postman/`:
- `postman/movie-api.postman_collection.json` (Postman Collection v2.1: `info.schema` is `https://schema.getpostman.com/json/collection/v2.1.0/collection.json`; requests are `item` entries with `request` and an `event` of `listen: "test"`; scripts are arrays of lines in `script.exec`).
- `postman/movie-api.postman_environment.json` with the variable `baseUrl` set to the placeholder `https://REPLACE-WITH-CLOUD-RUN-URL` (no trailing slash).
- `postman/README.md`: how to import both files, how to get the URL (`gcloud run services describe movie-api --region us-central1 --format 'value(status.url)'`), how to run the whole collection with the Collection Runner (or `npx newman run postman/movie-api.postman_collection.json -e postman/movie-api.postman_environment.json`), what each folder checks, and the data warning below.

Never edit `src/`. Do not call the deployed service yourself unless the user gives you the URL and says yes, because every run adds a movie to the real database. You may check that the JSON parses (`python3 -m json.tool`) and, if Node.js is available, syntax-check the scripts.

## Folders (they run in this order; later ones use variables set earlier)
1. **01 Add movie**
   - First request's pre-request script sets collection variable `runId` = `Date.now().toString(36)` (lowercase letters and digits).
   - `POST /api/v1/movies` with the fixture (below), the name padded with spaces: expect `201`, a `Location` header ending with the new id, a JSON body containing an `id` and every submitted field, and the name **trimmed**. Save `movieId`.
   - `GET` the `Location` URL: `200` with the same data.
   - Same body again, and again with the name in different case and extra spaces: both `409` with a `ProblemDetail`.
   - Invalid bodies, one request each, otherwise valid and with a distinct name such as `Pmtest invalid {{runId}}`: missing `name`, blank `name`, `year` 1800, `year` 3000, empty `genres`, `rating` 0, `rating` 6, `ageLimit` 19, `ageLimit` -1, an actor with a blank `lastName`, malformed JSON. Each expects `400` with a `ProblemDetail`.
   - `GET /api/v1/movies/does-not-exist-{{runId}}`: `404` with a `ProblemDetail`.
2. **02 List movies**
   - `GET /api/v1/movies`: `200`, content type JSON, body with `items` (array), `page` 0, `size` 20, `totalItems` (at least 1), `totalPages` equal to `ceil(totalItems / size)`; at most 20 items; every item has `id`, `name`, `year`, `genres`, `ageLimit`, `rating`.
   - Items are sorted by name ascending, then id: compare lowercase names with `<` (not `localeCompare`, the server sorts by code unit).
   - `?page=0&size=2`: at most 2 items, `size` 2, `totalPages` consistent. `?size=100`: accepted. `?page=9999&size=2`: `200`, empty `items`, the same `totalItems` as before.
   - `400` with a `ProblemDetail` for `size=101`, `size=0`, `page=-1` and `page=abc`.
3. **03 Search (one field at a time)**, one request per case. Every search uses `size=100`. Positive cases assert that **every** returned item satisfies the criterion (loop over `items`) and that the fixture (`movieId`) is among them. Negative cases assert the fixture is **not** returned.
   - `name` (substring, case-insensitive): `pmtest {{runId}}` -> exactly one item (`totalItems` 1, which also proves rejected and duplicate requests created nothing); upper-case version; only `{{runId}}` (a substring that is not a prefix); a name that matches nothing -> `totalItems` 0, empty `items`, `totalPages` 0.
   - `minYear` / `maxYear`: both 1900 (exact year); only `minYear=1900`; only `maxYear=1900`; `minYear=1901` excludes the fixture; `maxYear=1899` excludes it.
   - `genre` (case-insensitive, any of the movie's genres): the unique genre, the same in upper case, the second genre `drama`; an unknown genre -> empty.
   - `viewerAge`: 18 includes the fixture and every item has `ageLimit <= 18`; 17 excludes it; 0 returns only items with `ageLimit` 0.
   - `minRating` / `maxRating`: `maxRating=1` includes it; `minRating=2` excludes it; `minRating=1&maxRating=1` includes it and every item has rating 1; `minRating=5` returns only items with rating 5.
   - `director` (first name or last name, every word, case-insensitive): first-name word; last-name word; both words; both words reversed; negative: first word of the director plus the surname of an actor (must not match), and an actor's name searched as director.
   - `actor` (same rule, all words must match the **same** actor): first-name word; last-name word; both words of the first actor; both words of the second actor; negative: first name of actor 1 plus last name of actor 2 (must not match), and the director's name searched as actor.
   - `synopsis` (every word, any order, case-insensitive): the unique word; a second word plus the unique word in reverse order; the unique word plus a word that is not there -> excludes the fixture.
   - Blank text next to one real field is accepted: `genre=%20&actor=<actor word>` -> `200`, includes the fixture.
   - Search with paging: the unique genre with `page=0&size=1` -> one item, `totalItems` 1; `page=1&size=1` -> empty `items`, `totalItems` still 1.
   - Errors, each `400` with a `ProblemDetail`: two search fields (`actor` + `director`, and `minYear` + `minRating`; the `violations` must name every search parameter received, as in `docs/api.md`), `minRating=5&maxRating=1`, `minYear=2010&maxYear=2000`, `viewerAge=121`, `viewerAge=-1`, `minRating=6`, `viewerAge=abc`.

## The fixture (unique per run)
Build every value from `runId` so searches are predictable and reruns never collide:
- `name` `0 Pmtest {{runId}}` (starts with `0`, so it sorts first and lands on page 0), `year` 1900, `ageLimit` 18, `rating` 1
- `genres`: `Pmgenre{{runId}}` and `Drama`
- `director`: first name `Pmdir{{runId}}`, last name `Lastdir{{runId}}`
- `actors`: (`Pmact{{runId}}`, `Lastact{{runId}}`) and (`Other{{runId}}`, `Person{{runId}}`)
- `synopsis`: `pmsyn{{runId}} alpha beta`

Do not depend on the seeded movies, their number or their content: other data may exist and the sample file may change. The only data the tests rely on is the fixture each run creates itself.

## Test script style
- Every request has at least one `pm.test("...", ...)` with a descriptive sentence, for example "returns 201 and a Location header ending with the new id". Use `pm.expect`; never use `console.log` as an assertion.
- Check status first, then content type, then body. Parse the body once with a guard (`let body; try { body = pm.response.json(); } catch (e) { body = null; }`) so a failure reads clearly. A `ProblemDetail` has content type `application/problem+json`, a numeric `status` equal to the HTTP status, a `title` and a `detail`.
- A collection-level test script (runs after every request) checks that no 4xx or 5xx body leaks internals (no `Exception`, no stack-trace lines such as `at com.`).
- Use `{{baseUrl}}` in every URL; no hardcoded hosts. In `url.query` use readable values (Postman encodes them) and keep `url.raw` percent-encoded.
- No strict response-time assertions: Cloud Run cold starts can take several seconds.
- Name requests `METHOD what - expected result`, for example `GET search viewerAge=17 - excludes fixture`. Give each folder a short description.

## Finish
Run the JSON parse check, then reply with: the folders and the number of requests and assertions, which contract rules each folder verifies, anything in the contract that could not be tested or looked inconsistent, and how to run the collection, including the reminders to set `baseUrl` and that **each run adds one movie named `0 Pmtest <runId>` to the real database**. There is no delete endpoint, so leftovers are removed by hand in the Firestore console.

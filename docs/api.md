# Movie API contract

REST service to list, search and add movies. This document is the human-readable contract; the
machine-readable one is the OpenAPI description served by the deployed service at `/v3/api-docs`
(Swagger UI at `/swagger-ui.html`).

| Endpoint | Purpose | Status |
|---|---|---|
| `POST /api/v1/movies` | Add a movie | Available |
| `GET /api/v1/movies/{id}` | Fetch one movie | Planned (next slices) |
| `GET /api/v1/movies` | List and search movies | Planned (next slices) |

## Conventions
- Base path `/api/v1`. Request and response bodies are JSON (`Content-Type: application/json`).
- Errors are RFC 7807 problem details (`Content-Type: application/problem+json`), see [Errors](#errors).
- There is deliberately no update or delete endpoint: the service is public and unauthenticated, so it
  only lists, searches and adds (ADR-0006).

## The movie resource

| Field | Type | In a request | In a response |
|---|---|---|---|
| `id` | string | Not accepted (an unknown field, `400`). | Always. 32 lowercase hex characters, derived from the normalized name and the year. |
| `name` | string | Required, 1-200 characters after trimming, no control characters. | Always. |
| `year` | integer | Required, 1888 to current year + 5. | Always. |
| `genres` | array of strings | Required, 1-5 entries, each 1-50 characters after trimming, no control characters. Free text. | Always, in the order given. |
| `ageLimit` | integer | Required, 0-18: minimum recommended viewer age in years. | Always. |
| `rating` | integer | Required, 1-5 stars. | Always. |
| `director` | `Person` | Optional; omit it or send `null` when unknown. | Always present; `null` when unknown. |
| `actors` | array of `Person` | Optional, at most 30 entries, billing order kept. | Always present; `[]` when none are known. |
| `synopsis` | string | Optional, at most 2000 characters after trimming. A blank synopsis counts as none. | Always present; `null` when there is none. |

`Person` is `{"firstName": string, "lastName": string}`; both are required, 1-100 characters after
trimming, no control characters. Multi-word last names are normal (`"Lee Jones"`, `"del Toro"`).

Text rules:
- Leading and trailing whitespace is removed from every text before checking and storing; case and
  inner spacing are kept as entered.
- Lengths count UTF-16 code units of the trimmed text (`String.length()` in Java).
- "Required" means present, not `null` and not blank.
- Names, genres and person names must not contain control characters (U+0000-U+001F,
  U+007F-U+009F, such as tab, line feed or carriage return). Control characters that are whitespace
  (tab, line feed, carriage return, U+001C-U+001F) at either end are trimmed like spaces; any other
  control character is rejected wherever it appears. Violation:
  `{"field": "name", "message": "must not contain control characters"}`. The synopsis may contain
  line breaks.
- Types are strict: integers must be JSON integers and texts must be JSON strings. `1986.5`, `1986.0`
  and `"1986"` for `year` are rejected, not converted, and so is `123` for `name`.

## Add a movie: `POST /api/v1/movies`

Adds one movie to the catalog. The id is never supplied by the client: it is derived from the
normalized `name` and the `year`, so the same film always gets the same id.

### Request
```http
POST /api/v1/movies
Content-Type: application/json
```
```json
{
  "name": "Aliens",
  "year": 1986,
  "genres": ["Action", "Adventure", "Sci-fi"],
  "ageLimit": 16,
  "rating": 5,
  "director": {"firstName": "James", "lastName": "Cameron"},
  "actors": [{"firstName": "Sigourney", "lastName": "Weaver"}],
  "synopsis": "Ellen Ripley is rescued by a deep salvage team after being in hypersleep for 57 years."
}
```

```bash
curl -i -X POST "$BASE_URL/api/v1/movies" \
  -H "Content-Type: application/json" \
  -d '{"name": "Aliens", "year": 1986, "genres": ["Action", "Adventure", "Sci-fi"], "ageLimit": 16, "rating": 5,
       "director": {"firstName": "James", "lastName": "Cameron"},
       "actors": [{"firstName": "Sigourney", "lastName": "Weaver"}],
       "synopsis": "Ellen Ripley is rescued by a deep salvage team after being in hypersleep for 57 years."}'
```

### Success: `201 Created`
- `Location` header: where the new movie can be found, as a path relative to the service,
  `/api/v1/movies/{id}`. (`GET /api/v1/movies/{id}` arrives in a later slice; until then the `id` in
  the body is the reference to keep.)
- Body: the movie as stored, with its `id`, trimmed texts, and every field present.

```http
HTTP/1.1 201 Created
Location: /api/v1/movies/53e87be920fdef6e6176fa0c355406d8
Content-Type: application/json
```
```json
{
  "id": "53e87be920fdef6e6176fa0c355406d8",
  "name": "Aliens",
  "year": 1986,
  "genres": ["Action", "Adventure", "Sci-fi"],
  "ageLimit": 16,
  "rating": 5,
  "director": {"firstName": "James", "lastName": "Cameron"},
  "actors": [{"firstName": "Sigourney", "lastName": "Weaver"}],
  "synopsis": "Ellen Ripley is rescued by a deep salvage team after being in hypersleep for 57 years."
}
```

A movie without the optional fields comes back with `"director": null`, `"actors": []` and
`"synopsis": null`.

### Failures

| Status | When | Body |
|---|---|---|
| `400 Bad Request` | The movie breaks one or more rules from [the movie resource](#the-movie-resource). | Problem detail with a `violations` list naming **every** offending field. |
| `400 Bad Request` | The body is missing, is not valid JSON (including content after the JSON object or a repeated key), has a field of the wrong JSON type or a number out of range, or contains a field that is not part of the movie (including `id`). | Problem detail; `violations` names the field when it can be identified. |
| `409 Conflict` | A movie with the same normalized name and year already exists (case, extra inner spaces and surrounding spaces in the name do not make a film different). | Problem detail with `movieId`, the id of the existing movie. |
| `413 Payload Too Large` | The body is larger than 256 KB (a valid movie is far smaller). | Problem detail. |
| `415 Unsupported Media Type` | The `Content-Type` is not `application/json`. | Problem detail. |
| `500 Internal Server Error` | The movie store failed or did not answer in time, or an unexpected error occurred. | Problem detail with a generic message per cause; details are only logged. |

When the store did not answer in time, the movie **may have been saved anyway**. Retrying the same
request is safe: a `409` on the retry means the first attempt was stored.

## Errors
Every error, on every endpoint, is an RFC 7807 problem detail with `Content-Type: application/problem+json`
and these members:

| Member | Meaning |
|---|---|
| `type` | `about:blank`: the status code says what kind of error it is. |
| `title` | The standard HTTP reason phrase, for example `Bad Request`. |
| `status` | The HTTP status code. |
| `detail` | A human-readable explanation of this occurrence. |
| `instance` | The request path. |
| `violations` | Only on `400` for a request body: `[{"field": ..., "message": ...}]`. `field` is a path such as `name`, `genres[1]`, `director.firstName` or `actors[2].lastName`. |
| `movieId` | Only on `409`: the id of the movie that already exists. |

Stack traces, exception names and storage details are never returned.

Spring's own errors use the same format, for example `404 Not Found` for an unknown path (`detail`
`"There is no endpoint at this path."`), `405 Method Not Allowed` for an unsupported method (such as
`GET /api/v1/movies` until listing exists) and `406 Not Acceptable` when the `Accept` header excludes
JSON.
Only requests that the web server rejects before they reach the application (for example a
malformed HTTP request line) get Spring Boot's default error JSON instead.

### `400`: rules broken
Every broken rule is reported, in field order. The `detail` counts them ("breaks 1 rule", "breaks 3 rules"). When `genres` or `actors` have too many entries, only
the count is reported, not each entry.
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The movie breaks 3 rules; see violations.",
  "instance": "/api/v1/movies",
  "violations": [
    {"field": "name", "message": "is required"},
    {"field": "rating", "message": "must be between 1 and 5"},
    {"field": "actors[0].lastName", "message": "is required"}
  ]
}
```

### `400`: unreadable body
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The request body is not a valid movie: it must be a JSON object with the documented fields and types.",
  "instance": "/api/v1/movies",
  "violations": [
    {"field": "year", "message": "has the wrong type"}
  ]
}
```
A field that is not part of the movie gives `{"field": "synopsys", "message": "is not a known field"}`,
and a number too large for an integer gives `{"field": "year", "message": "is out of range"}`.
When no single field is to blame (the body is missing, is not valid JSON, is not an object, repeats
a key, or has content after the object), `violations` is omitted.

### `409`: duplicate
After `Aliens` (1986) was added, posting `"name": "aliens"` with `"year": 1986`:
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "A movie with the same name and year as 'aliens' (1986) already exists.",
  "instance": "/api/v1/movies",
  "movieId": "53e87be920fdef6e6176fa0c355406d8"
}
```

The `detail` repeats the name as submitted; the stored movie may spell it with different case or spacing.

### `413`: body too large
```json
{
  "type": "about:blank",
  "title": "Payload Too Large",
  "status": 413,
  "detail": "The request body is too large; a movie fits in 256 KB.",
  "instance": "/api/v1/movies"
}
```

### `500`: server error
When the movie store failed or did not answer in time:
```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "The movie store failed or did not answer in time. If you were adding a movie, it may have been saved; retrying is safe.",
  "instance": "/api/v1/movies"
}
```
Any other unexpected failure has the `detail` `"An unexpected error occurred."`.

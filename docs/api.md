# Movie API contract

REST service to list, search and add movies. This document is the human-readable contract; the
machine-readable one is the OpenAPI description served by the deployed service at `/v3/api-docs`
(Swagger UI at `/swagger-ui.html`).

In the examples, `$BASE_URL` is the service URL without a trailing slash, for example
`BASE_URL=https://movie-api-719585522889.europe-north1.run.app` (see [docs/deployment.md](deployment.md)).

| Endpoint | Purpose | Status |
|---|---|---|
| `POST /api/v1/movies` | Add a movie | Available |
| `GET /api/v1/movies/{id}` | Fetch one movie | Available |
| `GET /api/v1/movies` | List and search movies (one search field per request), a page at a time | Available |

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
  `/api/v1/movies/{id}`, which [fetches it](#fetch-one-movie-get-apiv1moviesid).
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

## Fetch one movie: `GET /api/v1/movies/{id}`

Returns one movie by its id. The movie comes from the service's in-memory copy of the catalog, the same
copy that listing and search read, so a movie is visible to both or to neither (ADR-0007).

```bash
curl -i "$BASE_URL/api/v1/movies/53e87be920fdef6e6176fa0c355406d8"
```

### Success: `200 OK`
The body is the movie in the same shape as the `201` body of [Add a movie](#add-a-movie-post-apiv1movies).

### Failures

| Status | When | Body |
|---|---|---|
| `404 Not Found` | No movie has this id. Any text that is not the id of a stored movie gives `404`; there is no separate format check (ids are 32 lowercase hex characters). | Problem detail, `detail` `"There is no movie with this id."`. The id is not repeated in the `detail`. |
| `500 Internal Server Error` | An unexpected error occurred. A failed catalog refresh is not one: the previous copy keeps being served. | Problem detail, see [`500`](#500-server-error). |

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "There is no movie with this id.",
  "instance": "/api/v1/movies/0123456789abcdef0123456789abcdef"
}
```

`GET /api/v1/movies/` (trailing slash, no id) is not this endpoint: it is a `404` with the detail
`"There is no endpoint at this path."`.

## List and search movies: `GET /api/v1/movies`

Returns the movies one page at a time. With no search field it lists the whole catalog; with one
search field it returns only the movies that match it. Search runs over the service's in-memory copy
of the catalog, the same copy that [Fetch one movie](#fetch-one-movie-get-apiv1moviesid) reads
(ADR-0007).

### Query parameters

All parameters are optional.

**Search fields: use at most one per request.**

| Parameter | Type | Matches |
|---|---|---|
| `name` | text | The movie name contains the text: `alien` finds "Alien" and "Aliens", `wars` finds "Star Wars". |
| `minYear` | whole number | `year >= minYear` (inclusive). Any whole number; a range outside the data simply matches nothing. |
| `maxYear` | whole number | `year <= maxYear` (inclusive). Use with or without `minYear`; `minYear` must not be greater than `maxYear`. For one exact year, set both to the same value. |
| `genre` | text | One of the movie's genres **equals** the text. Not a substring: `sci` does not find "Sci-fi". One genre per request. |
| `viewerAge` | whole number, `0` to `120` | Movies a viewer of this age may watch: `ageLimit <= viewerAge`. A viewer exactly at the age limit may watch; `viewerAge=0` returns only movies with age limit `0`. |
| `minRating` | whole number, `1` to `5` | `rating >= minRating` (inclusive). |
| `maxRating` | whole number, `1` to `5` | `rating <= maxRating` (inclusive). Use with or without `minRating`; `minRating` must not be greater than `maxRating`. |
| `director` | text | The movie has a director, and **every word** of the text is contained in the director's first name or last name. |
| `actor` | text | **One single actor** has every word of the text in their first name or last name. The words are never spread over several actors: `ryan wright` does not find a movie with Ryan Gosling and Robin Wright. |
| `synopsis` | text | The movie has a synopsis, and **every word** of the text is contained in it, in any order. |

**Paging.**

| Parameter | Type | Default | Allowed |
|---|---|---|---|
| `page` | whole number | `0` | `0` or more. Pages are numbered from `0`. |
| `size` | whole number | `20` | `1` to `100`: the number of movies per page. |

#### The one-field rule
- One search field is one row of the table above, except that `minYear` + `maxYear` together are one
  field (year), and `minRating` + `maxRating` together are one field (rating). `page` and `size` are
  not search fields and can be combined with any search.
- No search field lists every movie. Two or more different search fields are rejected with `400`,
  and every search parameter received is named in `violations` (see [Failures](#failures-2)).
  Filters are never combined, and an extra field is never silently ignored.
- A parameter counts as received when it has a non-blank value, even when that value is unusable
  (not a whole number, out of range, or given more than once). So `?minYear=abc&genre=drama`
  reports both the bad number and the combination in one response.

#### How text is matched
- Text is compared after normalizing both sides: Unicode NFC, trimmed, runs of inner whitespace
  collapsed to one space, lowercase. So matching ignores case and extra spaces: `?name=STAR   wars`
  finds "Star Wars".
- A *word* is a whitespace-separated part of the text. A word matches when it is contained in the
  name or synopsis, not only when it equals a whole word there: `miss` finds "missing", and `ford`
  finds "Harrison Ford". Repeating a word changes nothing.
- `name` is matched as a whole: the normalized text must appear in the normalized name, with its
  words in that order (`wars star` does not find "Star Wars").
- `director` and `actor` match word by word against one person's first name and last name, in any
  order: `ford`, `harrison`, `harrison ford` and `ford harrison` all find Harrison Ford, and
  `lee jones` finds Tommy Lee Jones (last name `Lee Jones`).
- Accents are not folded: `amelie` does not find "Amélie".
- *Whitespace* means Java's `Character.isWhitespace`, as for the movie id. The non-breaking space
  (U+00A0) is not whitespace: `harrison%C2%A0ford` is a single word and does not find Harrison Ford,
  and a value of only a non-breaking space is not blank.
- A movie without the searched field (no director, no actors, no synopsis) never matches that search.

#### Values
- Values are trimmed. An empty or blank value (`?name=`, `?name=%20%20`) counts as not given: for
  `page` and `size` the default applies, and a blank search field does not count toward the one-field
  rule. So `?name=&genre=drama` is a genre search.
- A *whole number* is one or more ASCII digits `0`-`9` with an optional leading `-`, within the 32-bit
  integer range. Anything else is `must be a whole number`, including `+5`, `1.5`, `1e2` and digits
  from other scripts.
- Every parameter may be given at most once. This includes empty values: `?name=&name=` is
  `must be given only once`, although it does not count as a search field.
- Values outside the allowed range are rejected with `400`, never clamped to the nearest allowed value.
- Any other parameter is rejected with `400`, even with an empty value, so a typo such as `?pgae=2`
  is noticed instead of silently returning page 0. This includes parameters that clients or tools
  append on their own, such as cache-busters (`?_=1697212800000`) and tracking tags (`utm_source`,
  `utm_campaign`, ...): do not add them to requests to this endpoint.

#### Examples (one search field each)
`curl -G --data-urlencode` encodes spaces and other special characters in the values.

```bash
curl -i "$BASE_URL/api/v1/movies"                                 # everything, first page
curl -i "$BASE_URL/api/v1/movies?page=1&size=10"                  # everything, second page of 10
curl -i "$BASE_URL/api/v1/movies?name=alien"
curl -i "$BASE_URL/api/v1/movies?minYear=2000&maxYear=2009"       # the 2000s (one field: year)
curl -i "$BASE_URL/api/v1/movies?minYear=1986&maxYear=1986"       # exactly 1986
curl -i "$BASE_URL/api/v1/movies?genre=sci-fi"
curl -i "$BASE_URL/api/v1/movies?viewerAge=12"
curl -i "$BASE_URL/api/v1/movies?minRating=4"
curl -i "$BASE_URL/api/v1/movies?director=spielberg"
curl -i -G "$BASE_URL/api/v1/movies" --data-urlencode "actor=harrison ford"
curl -i -G "$BASE_URL/api/v1/movies" --data-urlencode "synopsis=missing father"
curl -i "$BASE_URL/api/v1/movies?genre=drama&page=2&size=5"       # paging works with any search
```

### Order
Movies are sorted by name, ignoring case, and movies with the same name by `id`. Precisely: by the
normalized name (the same normalization as the id: Unicode NFC, trimmed, inner whitespace collapsed,
lowercase), then by `id`, both compared character by character (UTF-16 code units), not by language
rules. So the order is the same for every request and every client, and a name starting with an
accented letter (`Être et avoir`) sorts after `z`. Searches use the same order.

### Success: `200 OK`
The same shape for listing and for searching. `GET /api/v1/movies?director=cameron` in a catalog
where Aliens is the only movie directed by a Cameron:
```json
{
  "items": [
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
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

| Member | Meaning |
|---|---|
| `items` | The matching movies on this page, in the order above, each with every movie field. At most `size` entries. |
| `page` | The page returned (the requested or default `page`). |
| `size` | The page size used (the requested or default `size`). |
| `totalItems` | The number of matching movies: the whole catalog when there is no search field. |
| `totalPages` | `ceil(totalItems / size)`; `0` when nothing matches. The last page is `totalPages - 1`. |

- Movies are filtered first, then sorted and paged, so the pages contain only matches.
- The last page holds the remainder and may have fewer than `size` items.
- A page past the end (`page >= totalPages`) is not an error: it returns `200` with `"items": []` and
  the true `totalItems` and `totalPages`.
- A search that matches nothing, or an empty catalog, returns `"items": []`, `"totalItems": 0` and
  `"totalPages": 0`. It is not a `404`.

### Failures

| Status | When | Body |
|---|---|---|
| `400 Bad Request` | More than one search field, a parameter out of range, a lower bound greater than its upper bound, a value that is not a whole number, a parameter given more than once, or a parameter that is not known. | Problem detail with the fixed `detail` `"The query parameters are invalid; see violations."` and a `violations` list naming **every** problem. |
| `500 Internal Server Error` | An unexpected error occurred. A failed catalog refresh is not one: the previous copy keeps being served. | Problem detail, see [`500`](#500-server-error). |

| Problem | `field` | `message` |
|---|---|---|
| `page` below `0` | `page` | `must be 0 or more` |
| `size` below `1` or above `100` | `size` | `must be between 1 and 100` |
| Not a whole number (`abc`, `1.5`, `+5`, or outside -2147483648 to 2147483647) | the parameter | `must be a whole number` |
| Given more than once (`?page=1&page=2`, `?name=&name=`) | the parameter | `must be given only once` |
| `viewerAge` below `0` or above `120` | `viewerAge` | `must be between 0 and 120` |
| `minRating` or `maxRating` below `1` or above `5` | the parameter | `must be between 1 and 5` |
| `minYear` greater than `maxYear` | `minYear` | `must not be greater than maxYear` |
| `minRating` greater than `maxRating` | `minRating` | `must not be greater than maxRating` |
| Two or more search fields | **each** search parameter received | `must not be combined with another search field; received: <fields>` |
| Not a parameter of this endpoint (`?pgae=2`, `?utm_source=x`) | the parameter | `is not a known parameter` |

- `min > max` is only checked when both bounds are usable. `?minRating=6&maxRating=3` reports only
  `minRating` `must be between 1 and 5`, and `?minYear=abc&maxYear=1990` only `must be a whole number`.
- In the one-field message, `<fields>` lists the search parameters received, in the order of the
  search table (not the order of the request), separated by `, `. The two bounds of one field are
  joined with ` + `: `?genre=drama&maxYear=2000&minYear=1990` gives
  `received: minYear + maxYear, genre`, once on each of `minYear`, `maxYear` and `genre`.
- Violations are listed in a fixed order: `page`, `size`, then each search parameter in the order
  of the search table (given more than once, not a whole number, out of range), then `min > max`,
  then the one-field violations, then unknown parameters.
- Only parameter names are echoed, never the rejected values.

`GET /api/v1/movies?page=-1&size=101&pgae=2`:
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The query parameters are invalid; see violations.",
  "instance": "/api/v1/movies",
  "violations": [
    {"field": "page", "message": "must be 0 or more"},
    {"field": "size", "message": "must be between 1 and 100"},
    {"field": "pgae", "message": "is not a known parameter"}
  ]
}
```

Two search fields, `GET /api/v1/movies?actor=ford&director=spielberg`:
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The query parameters are invalid; see violations.",
  "instance": "/api/v1/movies",
  "violations": [
    {"field": "director", "message": "must not be combined with another search field; received: director, actor"},
    {"field": "actor", "message": "must not be combined with another search field; received: director, actor"}
  ]
}
```

`GET /api/v1/movies?minYear=2010&maxYear=2000&minRating=9`, an inverted year range, a rating out of
range, and two search fields:
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The query parameters are invalid; see violations.",
  "instance": "/api/v1/movies",
  "violations": [
    {"field": "minRating", "message": "must be between 1 and 5"},
    {"field": "minYear", "message": "must not be greater than maxYear"},
    {"field": "minYear", "message": "must not be combined with another search field; received: minYear + maxYear, minRating"},
    {"field": "maxYear", "message": "must not be combined with another search field; received: minYear + maxYear, minRating"},
    {"field": "minRating", "message": "must not be combined with another search field; received: minYear + maxYear, minRating"}
  ]
}
```

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
| `violations` | Only on `400`: `[{"field": ..., "message": ...}]`. For a request body, `field` is a path such as `name`, `genres[1]`, `director.firstName` or `actors[2].lastName`; for query parameters it is the parameter name, such as `size`. |
| `movieId` | Only on `409`: the id of the movie that already exists. |

Stack traces, exception names and storage details are never returned.

Spring's own errors use the same format: `404 Not Found` for an unknown path (`detail`
`"There is no endpoint at this path."`), and `405`, `406` and `415`, see
[below](#405-406-and-415-requests-spring-rejects).
Only requests that the web server rejects before they reach the application (for example a
malformed HTTP request line) get Spring Boot's default error JSON instead.

### `405`, `406` and `415`: requests Spring rejects
These come from Spring MVC, not from this application's code. They keep the problem shape above, but
the `detail` wording is Spring's own and is not part of the contract. Each is pinned by a controller test.

| Status | When | Example |
|---|---|---|
| `405 Method Not Allowed` | A method the path does not support. | `curl -i -X DELETE "$BASE_URL/api/v1/movies"` |
| `406 Not Acceptable` | The `Accept` header excludes JSON. | `curl -i -X POST "$BASE_URL/api/v1/movies" -H "Content-Type: application/json" -H "Accept: text/plain" -d '{}'` |
| `415 Unsupported Media Type` | `POST` with a `Content-Type` other than `application/json`. | `curl -i -X POST "$BASE_URL/api/v1/movies" -H "Content-Type: text/plain" -d 'x'` |

```json
{
  "type": "about:blank",
  "title": "Method Not Allowed",
  "status": 405,
  "detail": "<Spring's description of the problem>",
  "instance": "/api/v1/movies"
}
```

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
a key, has content after the object, or has a number longer than 1000 digits), `violations` is omitted.

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

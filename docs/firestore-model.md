# Firestore data model

Firestore is the durable store only (ADR-0003, ADR-0007). The application creates one movie at a time and reads all movies back; searching and get by id run in memory. How failures and mapping work is decided in [ADR-0010](adr/0010-storage-errors-and-document-mapping.md).

## Database and collection
- **Database:** `(default)`, Native mode. Named databases are not used because the free tier does not apply to them.
- **Collection:** `movies`, one document per movie.
- **Document id:** the movie id, `MovieId.derivedFrom(name, year)`: the first 32 hex characters of SHA-256 over `normalize(name) + "|" + year` (ADR-0006). The id is not repeated as a field. Because the id is deterministic, `DocumentReference.create` fails with `ALREADY_EXISTS` for a second movie with the same normalized name and year, which the repository reports as `DuplicateMovieException`.

## Document fields
Written by `MovieDocument.fieldsOf` plus `createdAt` from `FirestoreMovieRepository`.

| Field | Firestore type | Present | Notes |
|---|---|---|---|
| `name` | string | always | Trimmed, not normalized. |
| `year` | integer | always | |
| `genres` | array of strings | always | Order kept. |
| `ageLimit` | integer | always | |
| `rating` | integer | always | |
| `director` | map `{firstName: string, lastName: string}` | only when known | The key is absent when there is no director; it is never stored as null. |
| `actors` | array of maps `{firstName: string, lastName: string}` | always | Order kept. An empty array when there are no actors. |
| `synopsis` | string | only when known | The key is absent when there is no synopsis. |
| `createdAt` | timestamp | always | Server timestamp of the create, for diagnostics only. Never read, ordered by or queried. |

Example document `movies/53e87be920fdef6e6176fa0c355406d8`:

```json
{
  "name": "Aliens",
  "year": 1986,
  "genres": ["Action", "Adventure", "Sci-fi"],
  "ageLimit": 16,
  "rating": 5,
  "director": {"firstName": "James", "lastName": "Cameron"},
  "actors": [{"firstName": "Sigourney", "lastName": "Weaver"}],
  "synopsis": "Ellen Ripley is rescued by a deep salvage team after being in hypersleep for 57 years.",
  "createdAt": "<server timestamp>"
}
```

There are **no derived or search fields** (no lowercase copies, no keyword arrays) and **no composite indexes**: matching text is normalized in memory at search time (ADR-0007).

## Reading is strict
`MovieDocument.toMovie` rebuilds a `Movie` from a document:
- A missing or null `director` or `synopsis` becomes null; missing or null `actors` becomes an empty list.
- Every other field must be present with the right type. Numbers must arrive as whole-number integers (`Long` or `Integer` within the `int` range); a `Double` such as `1986.0` is rejected. `createdAt` and unknown keys are ignored.
- A wrong document fails with a message naming the document id and the field path, for example `Movie document <id>: field 'actors[1].lastName' must be a string but is nothing`. The repository wraps it in `MovieStorageException`, so **one corrupt document fails the whole load** loudly instead of producing a partial catalog. Fix the document in the Firestore console.
- The domain rules (`MovieRules`) are not re-applied on read (ADR-0009), so movies stay loadable after a rule is tightened.

## Package cycle between `service` and `persistence` (intended)
`MovieRepository` (in `persistence`) takes and returns the domain `Movie` and throws `DuplicateMovieException` and `MovieStorageException` (in `service`), while the service will depend on the `MovieRepository` interface. This two-way package dependency is deliberate: the domain stays the shared language, and the service never sees Firestore types, which stay inside `persistence` (plus `common/FirestoreConfig`, which creates the client). Do not "fix" it by moving `Movie` or the exceptions into `persistence`.

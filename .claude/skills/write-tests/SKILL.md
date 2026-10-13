---
name: write-tests
description: Conventions and checklists for writing unit tests in the Movie API (JUnit 5, Mockito, AssertJ, @WebMvcTest) - search filters, catalog, service, controller, seed loader. Use whenever tests are written, reviewed or fixed, or coverage is discussed, even if the user just says "add tests" or "test this".
---

# Writing unit tests for the Movie API

This project uses fast unit tests only: no Docker, no database, no emulator, no running server.
Because search is plain Java in memory (ADR-0007), most behavior can be tested thoroughly without any infrastructure.
Good tests serve two purposes: they catch regressions, and they document behavior for the next developer,
so optimize for readability and determinism over cleverness.

## Naming and structure
- File: `MovieSearchTest`, `MovieServiceTest`, `MovieControllerTest`. Mirror the production package under `src/test/java`.
- Method: `shouldXWhenY`, e.g. `shouldExcludeMovieWhenAgeLimitIsAboveViewerAge`.
- Body in three blocks marked with comments `// given`, `// when`, `// then`. One behavior per test.
- Use AssertJ (`assertThat(...)`), not JUnit assertions, for readable failure messages.
- Use `@Nested` classes to group by filter or scenario; use `@ParameterizedTest` for boundary tables.

## Test data
Use a test-data factory in `src/test/java/.../MovieTestData.java`:
`aMovie().withName("Aliens").withYear(1986).withAgeLimit(16).withRating(5).withDirector("James", "Cameron").build()`.
Defaults must be valid (genres, ageLimit, rating, a director) so each test states only what matters to it.
For search tests also provide a small shared catalog (5-8 movies modeled on the real sample data) in one place.

## Which tool for which part
| Part | Approach | Notes |
|---|---|---|
| `MovieSearch`, `MovieSearchCriteria` | plain JUnit, no mocks | The core. Full matrix, including the one-field rule, in the `movie-search` skill |
| `MovieCatalog` | plain JUnit + Mockito (`MovieRepository`) + controllable test clock (`MutableClock`) | Initial load, `include` after create (no duplicate id, next reload unchanged), no reload before the interval, reload exactly at it, failed refresh keeps the old snapshot and waits a full interval, merge of movies included during a reload |
| `MovieService` | Mockito (`MovieRepository`, `MovieCatalog` or real catalog with a stub repository) | Create: an invalid movie -> `InvalidMovieException` from `MovieRules` (rules themselves are tested in `MovieRulesTest`), duplicate -> `DuplicateMovieException`, catalog updated; get by id; search delegates correctly |
| Controller | `@WebMvcTest(MovieController.class)` + `MockMvc`, mocked service | Parameter binding for every filter, defaults, `Location` header, validation errors, response shape (`items`, `page`, `size`, `totalItems`, `totalPages`) |
| Error handling | `@WebMvcTest` with the `@RestControllerAdvice` | Assert the `ProblemDetail` body (status, title, detail), not only the status code |
| Pure helpers | plain JUnit | Normalization and trimming, deterministic id generation (golden values computed outside the code), `MovieRules` (every boundary with a fixed `Clock`), `MovieDocument` mapping (embedded people and genre lists) |
| Seed loader | `@JsonTest` + `@Import(JacksonConfig.class)` for the production mapper, Mockito (mock `MovieService`, `MovieCatalog`) | Reads the JSON, calls `createMovie` per entry, skips unconvertible, invalid or duplicate entries without failing, stops on a storage failure, fails on a non-array file, does nothing when the catalog is not empty |

The Firestore adapter (`FirestoreMovieRepository`) and `FirestoreConfig` have no automated tests by design (ADR-0003).
`MovieApiApplication` has none either: it only bootstraps Spring, and a test for it would need a full Spring context (ADR-0008).
Keep them thin so there is little to get wrong, and move any real logic into the pure classes above where it can be tested.

## Required cases per use case
- **List/search:** each search field on its own, and rejection of more than one field (see the matrix in the `movie-search` skill); defaults for `page` and `size`; invalid parameters give `400` with a `ProblemDetail` (`viewerAge` out of range, rating outside 1-5, `minRating` above `maxRating`, `minYear` above `maxYear`, `size` above 100, negative `page`); an empty result is an empty page, not an error.
- **Create:** valid request returns `201` + `Location` + body; missing or blank `name`, too-long name, year out of range, empty `genres`, `rating` outside 1-5, `ageLimit` outside 0-18, an actor with a blank last name each return `400`; duplicate returns `409`; the created movie is immediately found by search.
- **Get by id:** found, not found `404`.
- **Helpers:** normalization edge cases (extra spaces, mixed case, non-ASCII); same name and year always give the same id (also with different case or extra spaces) and a different year gives a different id; leading/trailing spaces in person names are trimmed.

## Quality rules
- Deterministic: no `Thread.sleep`, no random data without a fixed seed, no wall-clock time (inject a `Clock`).
- Independent: each test creates what it needs.
- Meaningful: if you can delete a line of production code and the test still passes, the test is not checking enough.
- Fast: no Spring context except the `@WebMvcTest` slices and the `@JsonTest` slice of the seed loader test.
- Do not test getters, records or framework behavior.
- When a test fails because the production code is wrong, do not weaken the test. Report the defect.

## Running
`./gradlew test` while iterating; `./gradlew check` for everything plus the JaCoCo gate (80% line coverage on `main` code, excluding the thin Firestore classes and `MovieApiApplication` listed above, ADR-0008).

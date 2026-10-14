# AI log 08: Search movies (one search field per request)

- **Date:** 2026-10-14
- **Goal:** let `GET /api/v1/movies` search by any one movie field (`name`, year, `genre`, `viewerAge`, rating, `director`, `actor`, `synopsis`), in memory over the catalog snapshot, and reject requests that combine fields with a `400` that names them.
- **Agents and skills used:** architect (Opus, ultrathink) for the plan review, unit-test-writer (Sonnet, think) for the whole test matrix before the implementation, code-reviewer (Opus, think harder) for the diff, doc-writer for the docs. Skills: movie-search, write-tests, document-change.

## Key prompts
1. > /model opus, plan mode — this is where the Opus budget belongs
   >
   > Plan mode, ultrathink. This is the slice the project is really about, so take it seriously and follow
   > the movie-search skill to the letter.
   > A request may use one search field, or none at all to list everything. Never two — that comes back as a
   > bad request that actually names which fields were sent, rather than quietly picking one. Year has a
   > lower and an upper bound that count together as a single field, and ratings work the same way. Blank
   > text means the field wasn't given at all.
   > Model the criteria as a closed set of types so the compiler forces us to handle every one of them when
   > a new filter appears, and keep the matching itself as ordinary Java with one small, well-named method
   > per filter. Two subtleties worth stating in the plan: for a director or an actor, every word the client
   > typed has to match the same person, not a mix of two people; and for the synopsis the words may appear
   > in any order.
   > architect agent on opus with ultrathink reviews before you write any code. Wait for me.
   > Then unit-test-writer agent on sonnet with think writes the whole matrix from the skill — a match, a
   > non-match and the boundary for every field, plus the rejection of multi-field requests. Expect red.
   > Implement until the gate is green, then code-reviewer agent on opus with think harder on the diff.
2. The user's answers to the contract questions the architect raised (see [Human decisions](#human-decisions)).

## Plan proposed
Planned in plan mode, reviewed by the architect, approved by the user:
- **`MovieSearchCriteria`**: a sealed interface with one record per field plus `All` (`NameContains`, `YearRange`, `Genre`, `ViewerAge`, `RatingRange`, `DirectorNamed`, `ActorNamed`, `SynopsisContains`). The records hold normalized text, drop duplicate words and reject invalid values with `IllegalArgumentException` (a bad record is a bug; client errors are reported by the parser). Missing year bounds become `Integer.MIN_VALUE`/`MAX_VALUE`, missing rating bounds `1`/`5`, so matching needs no null checks.
- **`QueryParameterValues`** (package-private): single value, blank-as-absent, "must be given only once" and whole-number parsing, moved out of `MovieQuery` so paging and search read parameters by the same rules.
- **`MovieQuery`** gains `criteria`; `parse` collects page, size, search and unknown-name violations into one `InvalidQueryException`.
- **`MovieSearch.search(movies, criteria, paging)`** (renamed from `pageOf`): filter once into a list, then sort and page, so the totals count matches. `matches` is an exhaustive `switch` over the sealed type with no `default`, and there is one small named method per filter (`nameContains`, `isReleasedWithin`, `hasGenre`, `isAllowedForViewerAged`, `isRatedWithin`, `isDirectedBy`, `hasActorNamed`, `synopsisContainsEvery`, shared `matchesEveryWord(Person, words)`).
- **`MovieService.searchMovies`** replaces `listMovies` (no overload); the controller declares the ten search parameters in its OpenAPI annotations and adds a second `400` example.
- Workflow: skeletons that compile and pass Spotless and Checkstyle with stubbed matching, then the test matrix, red, then the implementation.

Architect (Opus) verdict: **Approve with changes**. Must fix, all folded in before any code was written:
- **A repeated search parameter must still count toward the one-field rule.** Otherwise `?actor=a&actor=b&director=x` reports only the repeat, and the client gets a second `400` for the combination after fixing it. Solved by passing the raw `Map<String, List<String>>` to the parser and counting every parameter with a non-blank value as given (the helper was renamed from the planned `QueryNumbers` to `QueryParameterValues`).
- **Explicit user approval of the error format**, because the skill said the *detail* names the fields and the user asked to follow the skill to the letter. Changing the public contract is ask-first.
- **Explicit user approval of substring word matching**, since "contains every word" can also be read as whole words.
- **The exact `min > max` violation** had to be defined, because tests and `docs/api.md` depend on it.

Adopted suggestions: violation order follows the parameters (page, size, each search parameter in table order, `min > max`, one-field, unknown); the one-field message is worded like the existing messages; existing tests and docs that used `genre` as the example unknown parameter switch to `pgae`; Javadoc on every nested record and compact constructor (Checkstyle); duplicate words dropped; no try/catch as control flow (a package-private `collect(parameters, violations)` returns the criteria or null); all 28 pairs of fields as a parameterized test; NBSP, accent and empty-actors edge cases. Naming: `isAllowedForViewerAged` instead of `isSuitableForViewerAged`, `matchesEveryWord` instead of `isNamed`, `Genre(text)`.

### Deviations from the approved plan
- The parsing lives in a package-private class `MovieSearchCriteriaParser` (`collect` and `FIELDS`), not on the interface: a method of an interface cannot be package-private, and `MovieQuery` must call `collect` without making it part of the public API. The public `MovieSearchCriteria.of` delegates to it. The plan had allowed this move once the interface file grew too long.
- `MovieSearchCriteriaParser.FIELDS` (the fields in table order, each with its parameters) is the single source of `MovieSearchCriteria.PARAMETERS`, which `MovieQuery` uses as its known names. This came from the code review (below); a new filter is added to `FIELDS`.

## Tests first
The unit-test-writer wrote the whole matrix against the stubs before any matching code existed: `MovieSearchCriteriaTest` (new), and new cases in `MovieSearchTest`, `MovieQueryTest`, `MovieServiceTest` and `MovieControllerTest`. The run was red for the right reason: **266 of 595 tests failed**, 246 of them with the stubs' `UnsupportedOperationException` and 20 as direct fallout (for example controller tests expecting `400` and getting `500`). The record invariant tests passed, because they only exercise the compact constructors, which already existed. This fixes the lesson from slice 07, where the tests were never seen failing before the implementation.

## Review findings
code-reviewer (Opus): no logic bugs; it ran `./gradlew check` itself.
- **Must fix, fixed:** the OpenAPI example for combined fields had a text-block line continuation that put about 25 spaces inside the message, so Swagger UI showed a message the API never returns. Each violation now sits on one line.
- **Should fix, all fixed:**
  - Stale Javadoc in `ApiExceptionHandler.handleInvalidQuery` and `InvalidQueryException` (they still described the slice 07 violation kinds and order).
  - The list of search parameters existed twice (`PARAMETERS` and the parser's `FIELDS`) with nothing tying them together: a parameter added only to `PARAMETERS` would have been known but left out of the one-field rule. `PARAMETERS` is now derived from `FIELDS`.
  - Literal non-breaking spaces (U+00A0) in test sources looked like ordinary spaces, and an editor or formatter that rewrites them would silently change what the tests check. They are now ` ` escapes, as in the existing tests.
  - Repeated blank values (`name=&name=`): reported as "must be given only once" but not counted as a field. Kept on purpose (same as `page`/`size`) and pinned with a test.
- **Nice to have, applied:** the `MovieSearch` class Javadoc no longer says "substring everywhere" (genre is exact); new tests for `minRating=3&maxRating=0` (only the range error), `minYear=1&minYear=2&maxYear=3` (only the repeat, no one-field violation) and a stored name with an NBSP not matching `star wars`; test names brought to `shouldXWhenY`; two tests duplicating parameterized cases removed; a misleading parameterized label corrected.
- **Rejected:** normalizing the query words twice (once when splitting, again in the record constructors) was left as is. `normalize` is idempotent, and the record constructors own the invariant that their text is normalized, so they must not rely on the caller having done it.

## Human decisions
- **Error format:** the `400` for combined fields keeps the fixed detail "The query parameters are invalid; see violations." and names the fields in `violations`: one violation per search parameter received, with the message `must not be combined with another search field; received: <names>`, the names in contract-table order and the two bounds of one field joined with ` + ` (for example `received: minYear + maxYear, genre`). This replaces the skill's earlier wording, where the detail named the fields. Recorded in ADR-0004 and ADR-0007.
- **Substring matching for words:** `miss` finds "missing"; `genre` stays an exact match. Recorded in ADR-0007.
- **`min > max`:** one violation on the lower bound, `must not be greater than maxYear` (or `maxRating`), only when both bounds are usable, so one mistake is not reported twice.
- Confirmed details of the contract:
  - repeated or unparsable search values still count as given for the one-field rule; repeated blank values (`name=&name=`) are `must be given only once` but do not count as a field, as for `page`/`size`;
  - `viewerAge` `must be between 0 and 120`, ratings `must be between 1 and 5`;
  - violation order: `page`, `size`, each search parameter in table order, `min > max`, one-field, unknown names;
  - the non-breaking space is not whitespace: an NBSP-only value is given, and `harrison ford` is one word;
  - accents are not folded; `totalItems`/`totalPages` count the matches.

## Outcome
- `./gradlew check` green: 597 tests, 0 failures, 99% instruction coverage. No quality gate loosened, no suppression added.
- No Firestore change and no new dependency: search stays in memory (ADR-0007).
- Commit: see git log (`feat(movies): search movies by one field`).

## Lessons
- Writing the full matrix first against compiling stubs, and confirming a red run with failing assertions rather than compile errors, makes the tests a real check of the implementation.
- Keep non-ASCII whitespace in test sources as `\u` escapes; tools rewrite invisible characters silently.
- When a list (here the search parameters) is needed in two shapes, derive one from the other instead of keeping two copies.
- Contract details that the skill or AGENTS.md word differently (error wording, matching semantics) need the user's explicit decision before code is written; the architect review is the place to surface them.

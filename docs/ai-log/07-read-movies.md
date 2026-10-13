# AI log 07: Read movies (get by id and list in pages)

- **Date:** 2026-10-14
- **Goal:** add the read side without search fields: `GET /api/v1/movies/{id}` from the in-memory catalog, and `GET /api/v1/movies` listing the whole catalog a page at a time in a fixed order.
- **Agents and skills used:** architect (Sonnet) for the plan review, unit-test-writer (Sonnet) for the tests, code-reviewer for the diff, doc-writer for the docs. Skills: add-endpoint, movie-search, write-tests, document-change.

## Key prompts
1. > Plan mode, think. The read side without any filtering yet: fetch a single movie by identifier, and
   > list the whole catalogue a page at a time. Page numbering starts at zero, the default page holds
   > twenty, a hundred is the most anyone may ask for, and anything outside that is a bad request rather
   > than a silent clamp. The order is fixed and predictable: by name, ignoring case, then by identifier as
   > a tiebreaker. The response carries the items plus enough counts for a client to page through.
   > Contract into docs/api.md first, architect agent on sonnet reviews, then unit-test-writer agent on
   > sonnet covers the awkward cases: nothing in the catalogue, a last page that isn't full, a page past the
   > end, the largest allowed page size and one over it. Implement, run the gate.
2. > Additionally remove MovieCatalog.isEmpty method as it is not used by the code

## Plan proposed
Planned in plan mode and approved by the user:
- **Contract first** in `docs/api.md`: get by id (`200`, or `404` "There is no movie with this id." without echoing the id) and list (`page` 0-based default 0, `size` 1-100 default 20, out of range `400`, page past the end `200` with empty `items` and true totals, order by normalized name then id).
- **Service:** `MovieCatalog.findById` (runs the refresh check like `movies()`), `isEmpty()` removed; `MovieQuery.parse` reads the raw parameter map and reports every problem at once as `InvalidQueryException`; `MoviePageRequest` (valid by construction, holds the defaults and the maximum); `MoviePage`; `MovieSearch.pageOf` (sort, `long` offset, paging); `MovieNotFoundException`; `MovieService.findMovie` and `listMovies`.
- **Web:** `MoviePageResponse`, `MovieMapper.toPageResponse`, the two controller endpoints with OpenAPI annotations. **Handler:** `404` and `400` handlers in `ApiExceptionHandler`.
- The search slices later add `MovieSearchCriteria` to `MovieQuery`, rename `pageOf` to `search(movies, criteria, paging)` and `listMovies` to `searchMovies(query)`.

Architect (Sonnet) verdict: **Approve with changes**. Required changes, all folded in:
- Type errors (`page=abc`) cannot be reported together with range errors when Spring binds the integers. Solved by taking the raw parameter map and parsing it in `MovieQuery`, so all problems come out in one `400`. This also made the planned `getName()` fix for Spring's type-mismatch message unnecessary.
- Update the `movie-search` skill, the stale Javadoc in `ApiExceptionHandler` (it mentioned "a future `MovieNotFoundException`") and complete the contract edits in `docs/api.md`.

Adopted suggestions: keep paging separate from search criteria; keep the defaults in one place (`MoviePageRequest`); one `400` exception for every query problem (`InvalidQueryException`, which replaces the planned `InvalidSearchCriteriaException`); decide the unknown-parameter policy now (the user chose reject); state that the sort compares by code unit, not by locale.

## Review findings
code-reviewer:
- **Must fix:** documentation still describing the previous slice (README status, AGENTS.md, the `movie-search` and `firestore-persistence` skills, ADR-0002, this log). Resolved by the doc-writer step.
- **Should fix, all applied:**
  - `500` documented on both GET operations;
  - a test that `GET /api/v1/movies/` (trailing slash) is the "no endpoint" `404`;
  - `verifyNoInteractions(movieService)` in the test with several invalid parameters;
  - `InvalidQueryException` has a fixed message, so unknown parameter names (client text) never reach a log line.
- **Nits applied:** whole numbers are ASCII digits with an optional leading `-` only (`Integer.parseInt` alone accepted `+5` and non-ASCII digits); test names changed to `shouldXWhenY`; a constants-only test and a redundant test removed; the violation count asserted in the unknown-parameter test; the `id` parameter description no longer reads like validation.

## Human decisions
- **Unknown query parameters are rejected with `400`**, like unknown body fields (ADR-0012), so typos are noticed and the policy never has to be tightened later. Consequence: cache-busters and `utm_*` tags get a `400`. Recorded in ADR-0002.
- `MovieCatalog.isEmpty()` removed because nothing used it (the seed loader uses `movies().size()`).

## What went wrong
- Tests and implementation were written at the same time instead of tests first. While the unit-test-writer was still editing, the main agent ran `./gradlew spotlessApply`, which removed not-yet-used imports from `MovieControllerTest`; they were restored by hand.
- Three test names broke Checkstyle `AbbreviationAsWordInName` (`NotAWholeNumber`, renamed to `NotWholeNumber`).
- Because of the concurrent writing, the tests were never observed failing for the right reason before the implementation existed: they only failed on compilation.

## Outcome
- `./gradlew check` green: 356 tests, 0 failures. No quality gate loosened, no suppression added.
- Commit: see git log (`feat(movies): get a movie by id and list the catalog in pages`).

## Lessons
- Do not run `spotlessApply` while another agent is editing files: it rewrites them under the other agent's feet.
- Let the test writer finish and confirm a red run (failing assertions, not only compilation) before implementing.
- Checkstyle treats a capitalized single-letter word (`AWhole`) as an abbreviation; avoid articles in camel-case test names.

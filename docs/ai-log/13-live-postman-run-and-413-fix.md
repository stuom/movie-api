# AI log 13: first full live Postman run, full-project review and the 413 fix

- **Date:** 2026-10-14
- **Goal:** run the full 84-request Postman collection against the deployed service, review the whole project against the Definition of done, and fix what the user chose: M1 (oversized bodies got `400` instead of `413`), S1 (follow-up for the live URL in the Postman environment) and S2 (no AI-log entry for `7efcd5e`).
- **Agents and skills used:** the main agent (environment file, run instructions, interpreting the run, the fix, these logs), `code-reviewer` (on Opus: full-project review against the Definition of done, then a review of this diff).
- **Commits:** `docs(ai-log): record the documentation audit` (S2, [AI log 12](12-documentation-audit.md)) and `fix(api): answer 413 for every request body over 256 KB` (M1, S1, this entry).

## Key prompts
1. > I've deployed and here's the service address: https://movie-api-719585522889.europe-north1.run.app. Put it into the Postman environment file and tell me exactly how to run the collection myself — I'll run it, since each run leaves real data behind. When I paste the results back, interpret any failures: contract bug, test bug, or just a cold start. After that, have the code-reviewer agent on opus with think harder go over everything built so far against the definition of done in AGENTS.md — gate clean, every search field tested three ways, the API documentation, the written contract and AGENTS.md all saying the same thing, no database types leaking out of storage, no filtered queries, an ADR behind every real decision and a log entry per slice. Report findings with file and line and how serious each one is. I'll decide what gets fixed.
2. The user pasted the Newman failure table: 3 failed assertions, all in `01 Add movie / POST movie larger than 256 KB - 413 problem`.
3. > Fix M1, S1 and S2; plan first

## What was done
- **Live URL:**
  - `postman/movie-api.postman_environment.json` now holds the live service URL. This reverses the "placeholder only, no real URL" rule from [AI log 11](11-postman-full-contract.md), on the user's instruction.
  - As a result, a run writes to the live database without any URL step, so the "ask first" rule in AGENTS.md is the only guard.
  - `postman/README.md`, `README.md` and the `postman-test-writer` agent no longer ask the reader to set `baseUrl`.
- **The run (by the user, with Newman):** every request passed except the `413` request. That request got `400` with "The request body is not a valid movie…" and the violation `synopsis` "has the wrong type", which made 3 of its assertions fail.
  - Diagnosis: a contract bug, not a test bug (the test asserts what `docs/api.md` promises) and not a cold start (the service sent a proper problem detail).
  - The run left one `0 Pmtest <runId>` movie, to be deleted by hand.
- **Full-project review:**
  - Passed: the quality gate (597 tests, about 99% line coverage), the search-field test matrix, the persistence boundary and read-all-only Firestore access.
  - Must fix: M1, the `413` bug, which the reviewer had reproduced before the run results arrived.
  - Should fix:
    - S1, the live URL;
    - S2, no log for `7efcd5e`;
    - S3, the `service`↔`persistence` package cycle has no ADR;
    - S4, 13 test names break `shouldXWhenY`;
    - S5, the OpenAPI limits are written as literals.
  - Ten nits (N1–N10). The user chose M1, S1 and S2; the rest stay open.
- **M1 fix:**
  - `ApiExceptionHandler.handleHttpMessageNotReadable` now searches the whole cause chain for Jackson's `StreamConstraintsException`, not just the direct cause.
  - It answers `413` only for the document-length limit, recognised by `getMaxDocumentLength` in Jackson's message.
  - ADR-0012 got a dated update.
- **Tests:** three new controller tests: a 300,000-character synopsis and 40,000 genres (`413`), and a 1001-digit `year` (`400`).
  - Run before the fix, all three failed.
  - The third exposed a second bug the review had not listed: a small body with an over-long number was answered `413` "too large", because there the limit error *was* the direct cause.

## Plan proposed
Approved in plan mode:
- **M1:** walk the cause chain, recognise the document-length limit by message, write the failing tests first, update ADR-0012.
- **S1:** README and agent wording; the decision recorded here.
- **S2:** a new AI log 12 written from `git show 7efcd5e` and the prompt log.
- Two commits: S2 first, then M1 and S1.

Considered and not chosen: counting body bytes in a servlet filter before Jackson (more code and a second place that knows the limit), and treating every `StreamConstraintsException` as `413` (wrong for a long number in a small body).

## Review findings
`code-reviewer` (on Opus) checked the diff and confirmed with a throwaway Jackson 2.21.4 program that the synopsis and genres bodies wrap the document-length error, while the 1001-digit year raises the number-length error as the direct cause.
- **Must fix:** none.
- **Should fix (fixed):**
  - the first cycle guard (`cause != cause.getCause()`) could never fire and missed longer cycles; it is now a depth cap, `MAX_CAUSE_DEPTH`;
  - the 1001-digit test now asserts that there are no `violations`, so it cannot pass through the int-overflow path instead;
  - `docs/api.md` now lists over-long numbers among the bodies without `violations`;
  - AI log 12 now says which file got which change in `7efcd5e`.
- **Nits (fixed):**
  - the ADR-0012 `Updated` line;
  - the `deploy-cloud-run` skill still said to set `baseUrl`;
  - this log and ADR-0012 now quote the same failure the same way;
  - AI log 12 lists the review nit that was not taken and no longer calls the ADR-0014 choice unreconciled;
  - Javadoc on `isDocumentLengthLimit`.

## Human decisions
- The user runs the Postman collection by hand; the agent never calls the live service.
- Fix M1, S1 and S2 now. S3–S5 and N1–N10 are deferred.
- Redeploying and rerunning the collection need a separate yes.

## Outcome
- `./gradlew check` passes (600 tests). The live service still runs the old code until the next deploy; after it, the collection's `413` request is expected to pass.
- Open from the full-project review: S3–S5 and N1–N10.

## Lessons
- Test a limit well past its boundary, not only just past it: a body 208 bytes over the limit and one 40 KB over it take different paths through Jackson.
- A live run finds what unit tests cannot; the reviewer predicted this failure from reading code, and the run confirmed it.

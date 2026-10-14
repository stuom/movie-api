# AI log 11: Postman collection covers the full contract

- **Date:** 2026-10-14
- **Goal:** bring the Postman collection in line with the finished API: every body rule at its boundary, the remaining body errors (malformed JSON, client-supplied `id`, wrong type, `413`) and the unknown-parameter case for search.
- **Agents and skills used:** `postman-test-writer` (on Sonnet: collection and README), `code-reviewer` (review of the diff), the main agent (orchestration, verification, this log).
- **Commit:** `test(postman): cover every body rule, body errors and 413`.

## Key prompts
1. > Ask the postman-test-writer agent on sonnet to build the collection now that the API is complete. It should follow its own brief exactly: a run that creates its own uniquely named fixture movie, then lists, then exercises each search field one at a time, with the error cases included. Do not let it call any real service — it writes the files and checks they parse, nothing more. Remind it that the environment file must ship with a placeholder address and no real URL, and that the readme beside it has to warn that every run leaves one extra movie in the live database with no way to delete it through the API. When it's done, summarise for me what's covered and anything in the contract it couldn't test.
2. > yes, review, log and commit

## Plan proposed
No separate plan: the collection already existed (69 requests from AI log 10), so the agent was told to check it against `docs/api.md` and extend it rather than rewrite it.

## What was done
- Folder `01 Add movie` gained 14 requests: 10 single-rule invalid bodies (missing and blank name, year 1800 and 3000, empty genres, rating 0 and 6, ageLimit 19 and -1, an actor with a blank last name), each asserting exactly one violation; malformed JSON (no `violations`, exact `detail`); a client-supplied `id`; `year` as a string; and a body over 256 KB (`413`, built in a pre-request script).
- Folder `03 Search` gained `name=pmtest&utm_source=x` (`400`, unknown parameter).
- The collection now has 84 requests and 485 assertions. `postman/README.md` describes the new requests and warns about leftovers.
- The environment file was unchanged: it already held the placeholder `https://REPLACE-WITH-CLOUD-RUN-URL`. No real URL appears in any of the files.
- Verification: both JSON files parse with Node and every test script compiles. Nothing was run against the deployed service or a mock.

## Review findings
- **Must fix (fixed):** the year 1800 and 3000 tests used `d{4}` without its backslash, so they would have failed against a correct service.
- **Should fix (fixed):** the malformed-JSON assertion could never fail; the invalid bodies reused the fixture's genre, so one stored by a regression would also break the "exactly the fixture" searches (they now use `Pminvalid<runId>`); the README and descriptions claimed exactly one movie per run and "its own name" for requests that share one name.
- **Nits (fixed):** exact `eql` for single-violation checks, full `413` detail, folder description lists every case.
- **Noted, not changed:** the `413` title is "Payload Too Large" in Spring 6.2; a future Spring version may call it "Content Too Large", and then this test, `docs/api.md` and ADR-0004 change together.

## Human decisions
- The agent runs on Sonnet and must not call any service; running the collection against the live database still needs an explicit yes.
- Not tested: `405`, `406`, `415` and `500`, whose wording is Spring's or the store's and not part of the contract.

## Outcome
- The collection covers the whole documented contract except the statuses above. It has not yet been run against the deployed service.

## Lessons
- Regexes inside Postman JSON need doubled backslashes; a parse and compile check does not catch a missing one, only a review or a real run does.
- Error-case requests should not share data with the fixture, so a regression shows up in one place.

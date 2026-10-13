# AI log 05: No control characters in names, genres and person names

- **Date:** 2026-10-13
- **Goal:** reject control characters in a new movie's name, genres and person names (a domain change to ADR-0006), while the synopsis may still contain line breaks.
- **Agents and skills used:** unit-test-writer (Sonnet) for the tests before the implementation, code-reviewer (Opus) for the diff, doc-writer for the docs. Skills: write-tests, document-change. No architect review (see Plan proposed).

## Key prompts
1. > Names, genres and person names should not allow control characters, change domain rule (ADR-0006) accordingly

   The suggestion came from the slice 04 code review (nice-to-have 7), which had been raised with the user as an open question rather than applied.

## Plan proposed
Planned in plan mode and approved by the user. The change is one check in one existing helper, `MovieRules.requiredText`, which is used by `name`, `genres[i]` and the director's and actors' `firstName` and `lastName`, but not by `synopsis`:
- after trimming (`String.strip`, so `Character.isWhitespace`), add `FieldViolation(field, "must not contain control characters")` when the text contains a `Character.isISOControl` character (U+0000-U+001F, U+007F-U+009F);
- report it in addition to a length violation;
- update the `@Schema` descriptions on `NewMovieRequest` and `PersonDto`, `docs/api.md`, AGENTS.md and ADR-0006.

No architect review: the rule was decided by the user, and the code change is a single check in an existing helper with no new type, dependency or layer.

Out of scope, because the user's decision does not cover them: Unicode line and paragraph separators U+2028/U+2029 (not ISO control characters) and format characters such as the bidi override U+202E or zero-width spaces. They are still accepted; this was raised with the user as an open question.

The rule applies to new movies only: stored movies are not re-validated on load (ADR-0009). The seed file was checked and contains no control characters. Normalization and the id algorithm are unchanged (still frozen).

## Red and green
- **Red:** the unit-test-writer wrote the tests first. 33 rejection tests failed against the old code; the acceptance tests (for example a synopsis with line breaks, whitespace controls trimmed from the ends) passed, as expected.
- **Green:** one check in `requiredText`, plus the `@Schema` descriptions.

## Review findings
code-reviewer (Opus). No Must fix.

Should fix, all applied:
- **Wrong Javadoc claim:** it said control characters at either end are trimmed. Only the whitespace ones (tab, LF, CR, U+001C-U+001F) are; NUL, ESC, DEL, U+0085 and the like are rejected even at an end. Fixed.
- **Missing test** for non-whitespace controls at an end: added with `"\0Aliens"`, `"Aliens\u001B"`, `"Aliens\u007F"` and `"\u0085Aliens"`.
- **`docs/api.md`** now explains which control characters at the ends are trimmed and which are rejected.
- **A raw, invisible NBSP in a test** was replaced with the `" "` escape.
- **"Single-line" overclaim** removed from the docs and Javadoc, because U+2028/U+2029 are not covered. The rule was not widened without the user.

Nice to have, applied:
- the message is a constant in `MovieRules`;
- U+001F added to the tested value sets;
- a test constant moved up.

## Human decisions
- **Decided by the user:** the rule itself and its scope (names, genres and person names; not the synopsis).
- **Review findings not applied:**
  - **Direct tests for `director.lastName` and `actors[i].firstName`:** they go through the same `requiredText` path as the tested fields; the reviewer agreed this is acceptable.
  - **`codePoints()` instead of `chars()`:** equivalent here, because surrogate code units are never ISO control characters.
- **Open question for the user:** whether to also reject U+2028/U+2029 and format characters (bidi overrides, zero-width spaces).

## Outcome
- `./gradlew check` green: 241 tests. No quality gate was loosened.
- Commit: see git log.

## Lessons
- "Trimmed" and "control character" overlap only partly: trimming uses `Character.isWhitespace`, the rule uses `Character.isISOControl`. Some controls are trimmed at the ends, others are rejected there. State and test both ends explicitly.
- A value made only of non-whitespace controls (for example `"\u0000"`) is not blank, so it gets "must not contain control characters", not "is required".

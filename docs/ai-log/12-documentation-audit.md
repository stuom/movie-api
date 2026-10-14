# AI log 12: documentation audit against the code as built

- **Date:** 2026-10-14
- **Goal:** bring every document level with the code as it actually stood after the first deploy: a README a stranger can follow, every endpoint and search field with a working example and its errors, Javadoc on public classes, ADRs that match what was built, and one AI-log entry per slice.
- **Agents and skills used:** `doc-writer` (on Sonnet, with the `document-change` skill: audit, then edits), `code-reviewer` (review of the diff), the main agent (orchestration).
- **Commit:** `7efcd5e` (`docs: align documentation with the code as built`). This entry was written afterwards, in the slice of [AI log 13](13-live-postman-run-and-413-fix.md), because the full-project review found the commit had no entry.

## Key prompts
1. > Use the doc-writer agent on sonnet with the document-change skill to bring the documentation level with the code as it actually stands now. The readme should let a stranger clone this and run the tests without asking anyone a question. The API document needs every endpoint and every search field with a working example and the error responses. Also the storage layout, any missing explanations on public classes, and one log entry per slice covering the prompts, the plan and anything we rejected along the way. Verify every claim against the code — do not describe what we intended. Where an early ADR no longer matches what we built, update it or supersede it rather than quietly rewriting history, and tell me about anything you couldn't reconcile instead of guessing.

   (2026-10-14T08:13.)
2. > yes, commit it

   (2026-10-14T08:33.) The user's approval of the edit plan between the audit and the edits is not in the prompt log; the agent's second report calls its plan "the approved Section B plan".

## Plan proposed
The agent worked in two phases. Phase 1 was a read-only audit with three sections:
- **A:** 26 discrepancies with file and line.
- **B:** an edit plan.
- **C:** 8 points it could not reconcile.

Phase 2 applied section B. Edits to instruction files (AGENTS.md, skills) were listed separately because they need the user's approval.

## What was done
- `README.md`: the deployed status replaced "early development"; a quick start for a fresh clone (`./gradlew test`, PowerShell and cmd variants, no credentials needed); a note that Gradle does not download a JDK; a "Run the API tests" section with the Newman command and the ask-first warning.
- `docs/api.md`: `BASE_URL` defined once; a section on the `405`/`406`/`415` requests Spring rejects, marked as Spring's wording and not part of the contract.
- `docs/deployment.md`: what actually happened with `roles/run.builder` (the Compute default account already had `roles/editor`, so the grant was skipped); the budget alert recorded as still pending.
- ADR-0002, 0004, 0009, 0010 and 0014: dated *Update* sections; the original decision text is kept.
- `docs/ai-log/00-planning-and-agents.md` and `10-postman-and-cleanup.md` added; the follow-up commits listed in 09.
- `MovieRules`: Javadoc on the 13 limit constants (lengths are `String.length()`, UTF-16 code units, after trimming).
- AGENTS.md: points to the ADR index instead of a count, and its tree lists `config/artifact-registry-cleanup-policy.json`.
- `document-change` skill: points to the ADR index, requires an entry or addendum for every follow-up commit, and says the prompt log is local and git-ignored.
- `.gitignore`: `slice-log/` (the local hook output) added.

## Review findings
- **Must fix:** none.
- **Should fix (fixed):**
  - the AI logs cite `slice-log/`, which is git-ignored, without saying so;
  - an inferred claim about the slice order;
  - "never start the application" in the README (`@WebMvcTest` boots a web-layer context);
  - the README's `JAVA_HOME` sentence contradicted its prerequisites.
- **Nits (fixed):**
  - a heading typo, and 405/406 described twice in `docs/api.md`;
  - handlers and exceptions mixed up in the ADR-0004 update;
  - "characters" instead of UTF-16 code units in `MovieRules`;
  - the `ea0e158` file list in AI log 00;
  - the ADR-0014 remark in AI log 10.
- **Not taken:** a `GET` example for `406` (only the `POST` case is tested).

## Human decisions
- Not reconciled, left to the user:
  - U+2028/U+2029 and zero-width or bidi characters in names (the ADR-0006 open question);
  - the suspected seed-data mistakes (Avengers: Endgame as 2018, "Downtown Abbey", "Familty").
- Approved with the edit plan: the cleanup policy is recorded as an update to ADR-0014, not a new ADR (the agent's recommendation).
- Rejected: rewriting old ADR text; dated updates were used instead.

## Outcome
- Documentation matched the code at `7efcd5e`, and `./gradlew check` passed. The commit itself had no AI-log entry, although it added the rule that every follow-up commit needs one; this entry closes that gap.

## Lessons
- A documentation pass is itself a slice and needs its own entry; the rule it writes applies to the commit that writes it.

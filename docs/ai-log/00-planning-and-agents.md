# AI log 00: Planning, agents and skills before the first slice

- **Date:** 2026-10-13
- **Goal:** set up the working method (AGENTS.md, ADR drafts, skills, subagents, hooks) and settle the slice order before any code was written.
- **Agents and skills used:** the main agent (Opus), the project agents `architect`, `unit-test-writer`, `code-reviewer`, `doc-writer`, later `postman-test-writer`; the six project skills.
- **Commits:** `fb7963b` (original commit: agents, skills, AGENTS.md, ADR-0001 to ADR-0007 drafts, seed data), `0fd3c5e` (remove the `integration-tester` agent), `b7c9f41` (add the `postman-test-writer` agent), `ea0e158` (fix contradicting skills and ADRs, add the prompt and slice log hooks), `30c80a0` (LF line endings for the hook scripts).

## Key prompts
The prompt log (`slice-log/`, written by the hooks added in `ea0e158`) starts at 2026-10-13T21:27, so the prompts behind `fb7963b`, `0fd3c5e` and `b7c9f41` are **not available**: the log did not exist yet. What the commits show is recorded under "What was done". The prompt log `slice-log/prompts.md` is local hook output, git-ignored and kept outside the repository, so the quotes in the AI log entries cannot be checked from the repository alone.

1. > Opus, no plan mode needed
   >
   > Read AGENTS.md, the seven ADR drafts and the six skills. There is no code yet - only the seed data.
   > Tell me two things. First, whether any ADR draft still contradicts what AGENTS.md now says, since the
   > rules have been edited since those drafts were written. Second, the order you would build this in as
   > small vertical slices, one use case each, with a sentence per slice on what it delivers and what it
   > deliberately leaves out. Keep it to one page. Write no code and change no files yet.
2. > Fix ADRs and get-by-id should read from the catalog. Do needed changes

(The first prompt was logged in two parts, and the log has character-encoding damage in one dash; the text above joins the parts and uses a plain hyphen. The wording is otherwise verbatim.)

## What was done
- **Original commit (`fb7963b`):** five agents (including an `integration-tester`), six skills with templates, AGENTS.md, ADR-0001 to ADR-0007 and the seed file. Prompts: not available.
- **`integration-tester` removed (`0fd3c5e`):** its commit message says testing is done with Postman instead of Docker locally. This matches AGENTS.md ("By design there is no local-run, emulator or integration-test setup"). Prompt: not available.
- **`postman-test-writer` added (`b7c9f41`):** an agent that writes the Postman collection run against the deployed service, plus edits to AGENTS.md, the deploy and persistence skills and ADR-0001, ADR-0003 and ADR-0006. Prompt: not available.
- **Contradiction review (prompts 1 and 2, `ea0e158`):** skills and ADR drafts that disagreed with AGENTS.md were fixed, and get by id was made to read from the in-memory catalog instead of Firestore (ADR-0003, ADR-0005, ADR-0006, ADR-0007, the add-endpoint and firestore-persistence skills and AGENTS.md were changed in `ea0e158`).
- **Hooks (`ea0e158`, `30c80a0`):** `.claude/hooks/log_prompt.py` and `log_slice.py` write every prompt and the agent's status messages to `slice-log/`, the source of the prompts quoted in the later AI log entries.

## Plan proposed
The project was then built in this order: 01 skeleton and quality gates, 02 domain model, 03 Firestore storage, 04 add a movie, then control characters (05), catalog and seed (06), read movies (07), search (08) and deployment (09). The agent's one-page answer to prompt 1 is not preserved in the prompt log.

## Review findings
Not applicable: no code was written and no code-reviewer run took place.

## Human decisions
- Testing is by unit tests plus a Postman collection against the deployed service; no Docker, emulator or integration-test suite (the `integration-tester` agent was removed).
- Get by id reads from the catalog; Firestore keeps only create and read all.

## Outcome
No build existed yet. After `ea0e158` the agents, skills, AGENTS.md and ADR-0001 to ADR-0007 no longer contradicted each other, as far as the review found.

## Lessons
- Review the written rules against each other before the first line of code: earlier edits to AGENTS.md had left the ADR drafts and skills out of date.
- Start the prompt log before the first prompt, so every prompt of the project can be quoted verbatim.

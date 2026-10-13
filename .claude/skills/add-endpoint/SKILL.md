---
name: add-endpoint
description: End-to-end workflow for delivering one vertical slice of the Movie API (a new or changed REST endpoint such as list, search or create movie). Use this whenever the user asks to add, change or implement an endpoint, use case, feature or "slice", even if they do not mention tests or docs, because the workflow guarantees contract, tests, review and documentation are all done.
---

# Deliver a vertical slice

A slice is one use case taken from contract to commit. Following the order below keeps quality high:
the contract is agreed before code exists, tests drive the implementation, and independent review
catches what the author misses. You (the main agent) orchestrate; subagents cannot call other subagents.

## 1. Understand
Read `AGENTS.md`, `docs/api.md` and the existing code in the affected feature package.
If the request is ambiguous (fields, filters, error cases), ask the user before planning.

## 2. Contract first
Draft the endpoint in `docs/api.md`: method, path, parameters, request and response bodies, status codes,
and one curl example. Writing the contract first forces decisions about naming and errors early.

## 3. Plan and design review
Enter plan mode and write a short plan: files to create or change, classes and method names,
validation rules, error cases, tests. Then ask the `architect` agent to review it.
Apply "Required changes", show the user the plan and wait for approval.

## 4. Tests first
Ask the `unit-test-writer` agent to write unit tests from the contract (service, controller, repository as relevant).
Expect them to fail at this point, which proves they test something.

## 5. Implement, bottom-up
Persistence (only if storage changes: `MovieDocument` mapping, `FirestoreMovieRepository`; see the `firestore-persistence` skill) -> service (rules, `MovieSearchCriteria`/`MovieSearch` for filters, see the `movie-search` skill, exceptions) -> web (DTO records, mapper, controller with OpenAPI annotations).
Run `./gradlew test` after each layer. Keep names intention-revealing and write Javadoc as you go, explaining *why*.
Storage changes (new fields) are documented in `docs/firestore-model.md`. New filters need no Firestore change (ADR-0007); document their semantics in `docs/api.md`.

## 6. Gate and review
Run `./gradlew check`. Fix every failure properly; never relax Checkstyle, SpotBugs or coverage rules to pass.
Then ask the `code-reviewer` agent to review the diff. Fix all "Must fix" items, and apply "Should fix" items unless there is a reason not to (say so in the AI log).

## 7. Document and commit
Follow the `document-change` skill (ask the `doc-writer` agent to do the heavy lifting: README, `docs/api.md`, ADRs, AI log).
Commit once with a conventional message, for example `feat(movies): add year range search filters`.

## Finish
Report to the user: what was built, test and gate results, review findings and how they were resolved, decisions that got an ADR, and suggested next slice. If the slice adds a query shape and the user deploys, remind them to run the smoke test from the `deploy-cloud-run` skill.

---
name: document-change
description: Procedure to document a finished change in the Movie API: update README, docs/api.md, Javadoc, write an ADR for design decisions, and add an AI-log entry capturing the prompts used. Use after completing any slice or decision, and whenever the user mentions documentation, ADRs, README, changelog or "document how we built this".
---

# Document a change

Documentation is part of the definition of done. Other developers (and anyone reviewing how this project
was built with AI assistance) rely on it, so write it for a reader who has never seen the code.
Only document what you have verified in the code or by running it.

## 1. API contract (`docs/api.md`)
For each endpoint: purpose, parameters (type, required, default, constraints), curl request example,
success response example, error responses (status + `ProblemDetail` example). Compare with the controller and the OpenAPI annotations;
they must say the same thing.

## 2. README
Keep it short and runnable: what the service does, prerequisites (Java 21), quick start, how to run tests and the quality gate,
Swagger UI location, links to `docs/`. Re-run the quick-start commands to confirm they work.

## 3. Javadoc
Every public class and method. Explain intent, rules, thrown exceptions, and why a non-obvious choice was made. Do not restate the method name.

## 4. ADR (only for significant decisions)
Write an ADR when the decision is hard to reverse or someone might ask "why did you do it this way?":
framework or library choices, database strategy, API shape, error format, deployment approach.
Copy `templates/adr-template.md` to `docs/adr/NNNN-short-title.md` (next free number), keep it to one page, and set a status.
ADR-0001 to ADR-0013 already exist (index in `docs/adr/README.md`) in `docs/adr/`: keep them accurate, and supersede rather than silently rewrite them when a decision changes. Also keep `docs/firestore-model.md` and the search semantics in `docs/api.md` in sync with the code.

## 5. AI log (`docs/ai-log/NN-slice-name.md`)
Copy `templates/ai-log-entry.md`. Record: the goal, the key prompts (verbatim, shortened if very long), the plan the agent proposed,
which subagents and skills were used and what they found, what the human changed or rejected and why, and the outcome (test and gate results).
Be honest about mistakes and corrections, because they show the review process working.

## 6. Verify
Run `./gradlew check` to confirm docs-related changes (Javadoc, annotations) did not break the build, and check that relative links in the docs resolve.

# AI log 10: Postman coverage for the read side and search, and the image cleanup policy

- **Date:** 2026-10-14
- **Goal:** record the follow-up work that was not part of a slice's own commit: the Postman requests for slices 07 and 08, and the Artifact Registry cleanup policy after the first deploy.
- **Agents and skills used:** `postman-test-writer` (Postman requests), the main agent (cleanup policy, deployment doc), skill deploy-cloud-run.
- **Commits:** `f91de5e` (Postman: get by id and paged listing) and `a8b266a` (keep only the two most recent container images). The `03 Search` folder came with `aee5fdd` (slice 08); the first Postman run against the live service is recorded in [AI log 09](09-deploy-cloud-run.md).

## Key prompts
1. Slice 07 Postman requests: the user's instruction is **not available** in the prompt log. The log shows only the `postman-test-writer` agent's hand-back (2026-10-14T01:12): it added the get-by-id requests and a folder `02 List movies`, and ran them only with Newman against a local mock server that follows `docs/api.md`, not against the deployed service.
2. > yes add the Postman requests, then commit

   (slice 08, 2026-10-14T07:27.) The agent added the `03 Search` folder with 51 requests and updated `postman/README.md`; nothing was run against the deployed service.
3. > yes, run the Postman collection

   (2026-10-14T08:10, after the first deploy.)
4. > yes, set up the Artifact Registry cleanup policy

   (2026-10-14T08:11.)

## What was done
- **`f91de5e`:** folder `01 Add movie` gained a get-by-Location request and an unknown-id `404`; the new folder `02 List movies` has 12 requests (paging, size limits, bad `page` and `size`, an unknown parameter). `postman/README.md` describes each request.
- **`aee5fdd`:** folder `03 Search` with 51 requests, one search field each, built around the run's own test movie so that no assertion depends on the sample data. The collection has 69 requests in total (6 + 12 + 51).
- **First live run:** Newman against the deployed service ran 69 requests with 448 assertions and 0 failures (details in AI log 09). It added the test movie `0 Pmtest mv7nior7`, which is removed by hand in the Firestore console.
- **`a8b266a`:** each deploy pushes an image of about 250 MB and Artifact Registry gives 0.5 GB free. `config/artifact-registry-cleanup-policy.json` keeps the 2 most recent images and deletes older ones. It is applied with `gcloud artifacts repositories set-cleanup-policies` and documented in `docs/deployment.md` and the deploy skill. The dated update in ADR-0014 about the policy was added later, in the documentation pass, not in `a8b266a`.

## Plan proposed
No separate plan: these were follow-ups to slices 07, 08 and 09, each approved with a short "yes" (prompts above).

## Review findings
No code-reviewer run: the work changed only the Postman files and deployment documentation. The Postman checks were verified against a mock for slices 07 and 08, and against the live service for the whole collection.

## Human decisions
- Run the collection against the real database only after an explicit yes, because every run adds one test movie that cannot be deleted through the API.
- Keep 2 images (the serving revision and one to roll back to); rolling back further needs a redeploy of an older commit.
- Rejected alternatives: none recorded in the prompt log for these commits.

## Outcome
- The Postman collection passed against the live service (69 requests, 448 assertions, 0 failures).
- The cleanup policy is set; Artifact Registry cleans in the background, typically within a day (`docs/deployment.md`).

## Lessons
- A follow-up commit of a slice needs its own AI log entry or an addendum, or its prompts and decisions are lost: these commits were missed until the documentation audit.
- A collection tested only against a mock proves its own assertions, not the service; the live run is what closed that gap.

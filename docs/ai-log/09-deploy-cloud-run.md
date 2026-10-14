# AI log 09: Deploy to Google Cloud Run (europe-north1)

- **Date:** 2026-10-14
- **Goal:** make `./gradlew deployCloudRun` a working one-command deployment to Google Cloud Run in `europe-north1`, with Firestore in the same region, and document the one-time cloud setup. The cloud setup and the first deploy are left to the user.
- **Agents and skills used:** architect for the plan review, doc-writer for the docs, code-reviewer for the diff. Skills: deploy-cloud-run, document-change.

## Key prompts
1. > deploy to Cloud Run

   The agent found that nothing was set up (see [Inventory](#inventory)) and asked whether to start a deployment slice.
2. > yes, start the deployment slice in plan mode, for location select closest to Finland, preferably in EU

## Inventory
What the agent found before planning:
- In the repository: no `deployCloudRun` task, no `gradle.properties`, no `Dockerfile`, no `.gcloudignore`/`.dockerignore` and no `docs/deployment.md`, although AGENTS.md described them. Templates existed in `.claude/skills/deploy-cloud-run/templates/`.
- In GCP project `movie-api-511411` (active, billing enabled): APIs disabled, no Firestore database, no runtime service account.
- The local gcloud default project is a different project, so every command must pass `--project movie-api-511411`.

## Plan proposed
Region **`europe-north1`** (Hamina, Finland). Verified on 2026-10-14: Cloud Run lists it under "Subject to Tier 1 pricing" (Low CO2), so the free tier applies (per billing account), and Firestore Native offers it as a regional location. Alternatives weighed: `us-central1` (the skill's old default), `europe-north2` (Stockholm), `eur3` multi-region (more expensive, no help for the free tier). Recorded in [ADR-0014](../adr/0014-deploy-to-cloud-run-and-firestore-in-europe-north1.md).

Repository changes, no cloud access:
- `gradle.properties` with the four deployment keys only (no Gradle performance flags, so `check` builds as before).
- The `deployCloudRun` `Exec` task (`dependsOn(check)`), reading the keys as providers and checking them in `doFirst`, `gcloud.cmd` on Windows.
- `Dockerfile` (JDK 21 build of `bootJar`, JRE 21 runtime as non-root `appuser`), `.dockerignore` and `.gcloudignore` (template list plus `postman/`, `slice-log/`, `config/`, `src/test/`).
- Graceful shutdown in `application.properties`.
- Region and wording fixes in the deploy skill, its templates, `postman/README.md` and the postman-test-writer agent.
- Docs: `docs/deployment.md`, ADR-0014, an ADR-0006 note, README, this log.

The one-time cloud commands and the deploy are listed for the user to run; nothing that creates cloud resources runs without a clear yes.

## Review findings
architect: **Approve with changes**. All folded into the plan:
- **Must fix:**
  - Read the deploy settings when the task runs: a missing key now throws a `GradleException` naming it and leaves `check` unaffected (the template read them while configuring, which would break every build).
  - Treat the first seed as a one-time, permanent write: get the user's decision on the suspected seed mistakes first; send a request right after the deploy so seeding gets CPU (Cloud Run throttles CPU between requests); check the logs for `Seeded 20 of 20 sample movies`; document the recovery (delete the partial documents in the console, redeploy).
- **Should fix:**
  - Check the Cloud Build service account up front (`gcloud builds get-default-service-account`) and grant it `roles/run.builder`; the skill's troubleshooting named the wrong role.
  - Enable `iam.googleapis.com`.
  - Document the IAM propagation delay (`PERMISSION_DENIED` right after the binding: wait, redeploy).
  - `MaxRAMPercentage` 60 instead of 75 within 512Mi.
  - No `./gradlew dependencies` layer in the Dockerfile (source builds do not reuse layers).
  - Document the hidden costs (Artifact Registry about 250 MB per deploy vs 0.5 GB free, the `run-sources-*` bucket, Cloud Build minutes).
  - Make the missing-key verification explicit.
- **Nice to have, applied:** shutdown phase timeout 8 s (Cloud Run gives about 10 s after `SIGTERM`), `--cpu-boost`, Firestore `--delete-protection` and a Standard edition check, "about 1,000 movies" corrected to about 500 in the skill (ADR-0013), "commit before deploying" stated (`--source .` uploads the working tree), `src/test/` left out of the upload.

code-reviewer (on the diff, run in parallel with the documentation):
- **Must fix, applied:** `deployCloudRun` was not configuration-cache friendly. Its `doFirst` captured the build script through script-level `val`s; Gradle reported "cannot serialize Gradle script object references". The settings providers and the `gcloud` launcher name are now task-local values inside `register`. A configuration-cache dry run reports no problems for `:deployCloudRun`. The remaining problems belong to the existing coverage guard in `check`'s `doLast` (slice 01); the configuration cache is not enabled, so that is left as it is.
- **Should fix, applied:**
  - The skill templates now match the repository files: `gradle.properties` without Gradle performance flags, the shutdown phase timeout, and the deploy task.
  - The skill says to use only the email from `gcloud builds get-default-service-account`, which prints `projects/<project>/serviceAccounts/<email>`.
- **Nits, applied:**
  - The map of setting values is renamed `values`.
  - `**/*.md` replaces `*.md` in the ignore files, with a note that `.dockerignore` patterns are relative to the root.
  - The JVM now runs with `-XX:+ExitOnOutOfMemoryError`.
  - The skill notes that the deployer needs Service Account User on `movie-api-run`; project owners already have it.
- **Not applied:** moving the JVM flags into `ENTRYPOINT` to avoid the "Picked up JAVA_TOOL_OPTIONS" log line. It is one line per start, and `JAVA_TOOL_OPTIONS` keeps the entry point simple.
- Afterwards, `--project movie-api-511411` was added to the `gcloud run services describe` commands in `postman/README.md`, the deploy skill and the postman-test-writer agent, because the local gcloud default project is a different one.

## Human decisions
- **Region:** closest to Finland, preferably in the EU, so `europe-north1`.
- **Seed data:** seed `movies.json` exactly as provided. The suspected mistakes from [AI log 06](06-catalog-and-seed.md#suspected-seed-data-errors) are stored permanently and can only be fixed in the Firestore console (recorded in ADR-0006).

## Outcome
- Verification reported by the main agent: `./gradlew check` green; with one deploy key removed temporarily, `./gradlew check` still passed, `./gradlew deployCloudRun --dry-run` listed the tasks, and running `deployCloudRun` failed with the message naming the missing key.
- **Cloud setup, run by the user** with the commands from the plan: the five APIs, the Firestore `(default)` database (europe-north1, Standard edition, free tier, delete protection), `movie-api-run` with `roles/datastore.user`.
- **Build account:** `gcloud builds get-default-service-account` returned the Compute Engine default account, which already has `roles/editor`, so `roles/run.builder` was not granted (no extra permissions).
- **First deploy** (user's yes): `./gradlew deployCloudRun` succeeded in 4 min 24 s; revision `movie-api-00001-w9p`, URL https://movie-api-719585522889.europe-north1.run.app.
- **Seed check:** a request was sent right after the deploy; the log shows `Catalog loaded with 0 movies`, then `Seeded 20 of 20 sample movies` about 30 s after startup. The API lists 20 movies.
- **Smoke checks:** list `200`, `/v3/api-docs` `200`, `/swagger-ui.html` `200`, `actor=ryan wright` returns no items, `actor` + `director` returns the documented `400`. The service runs as `movie-api-run` with max 1 instance.
- **Still pending:** the Postman run (separate consent: each run adds a `0 Pmtest` movie) and the budget alert (Billing console, by the user).
- Commit: see git log (`feat(deploy): one-command deployment to Cloud Run in europe-north1`).

## Lessons
- Backslash escapes inside Bash heredocs got mangled in this environment. Scripts that contain escapes go into files in the scratchpad and are run from there.
- Before the first deploy, list everything that becomes permanent (the Firestore location, the seeded data) and get the user's decision on each; there is no undo for either.
- Read build settings that only one task needs when that task runs, so a missing setting cannot break unrelated tasks such as `check`.

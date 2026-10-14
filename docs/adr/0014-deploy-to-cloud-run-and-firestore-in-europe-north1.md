# ADR-0014: Deploy to Cloud Run and Firestore in europe-north1

- **Status:** Accepted
- **Date:** 2026-10-14

## Context
The service has to go live on Google Cloud Run in project `movie-api-511411`, with Cloud Firestore as the store (ADR-0003). Before slice 09 nothing was set up: no APIs enabled, no database, no service account, and no deploy task in the repository. Two kinds of decision were needed:

- **Where.** The user asked for the region closest to Finland, preferably in the EU. The Firestore location is chosen once, when the database is created, and **can never be changed**; moving later means a new project or an export/import. Cloud Run and Firestore should share the region, because every catalog load and every create is a call from the service to the database. The project should stay inside the free tier: the Cloud Run free tier applies only to Tier 1 pricing regions, and the Firestore free quota only to the `(default)` database.
- **How.** AGENTS.md already described a one-line `./gradlew deployCloudRun`, but the approach (build path, identity, scaling) was recorded nowhere as a decision.

Region facts were checked on 2026-10-14 in the Cloud Run locations and pricing pages and the Firestore locations page (links in `docs/deployment.md`).

## Options considered
1. **`us-central1`** (the deploy skill's original default): Tier 1, the usual default in examples. But data leaves the EU and the latency from Finland is highest of the options.
2. **`europe-north1`** (Hamina, Finland): in Finland and the EU, so the lowest latency for Finnish users; listed under "Subject to Tier 1 pricing" (Low CO2), so the Cloud Run free tier applies; offered by Firestore Native as a regional location.
3. **`europe-north2`** (Stockholm, Sweden): also Nordic and in the EU, but outside Finland, so farther away than Hamina, with no advantage for this service.
4. **`eur3` multi-region** (Europe) for Firestore: higher availability, but more expensive and no help for staying in the free tier; it is a Firestore location only (Cloud Run would still need a region), and the extra availability is not needed here.

For the deploy approach:
- **Source deploy** (`gcloud run deploy --source .`, built by Cloud Build) vs a local Docker build and push vs a CI pipeline: source deploy needs no local Docker and no pipeline to maintain; CI would add traceability but is more setup than this project needs now.
- **Runtime identity:** the default compute service account (broad rights) vs a dedicated account with only `roles/datastore.user`.
- **Scaling:** `min-instances 0` (free when idle, cold starts) vs 1 (no cold starts, always billed); `max-instances 1` vs more (ADR-0007: one in-memory catalog per instance).

## Decision
- **Region `europe-north1` for both Cloud Run and Firestore.** Firestore: Native mode, the `(default)` database, Standard edition, created with `--delete-protection`.
- **Source deploy via Cloud Build, run from a developer machine, no CI.** `./gradlew deployCloudRun` runs `check` first, then `gcloud run deploy --source .`. All settings live in `gradle.properties` and the task in `build.gradle.kts`; the task checks the settings only when it runs, so a missing key cannot break `check`. The Cloud Build service account gets `roles/run.builder`.
- **Least-privilege runtime account** `movie-api-run@movie-api-511411.iam.gserviceaccount.com` with only `roles/datastore.user`. No key files: Application Default Credentials.
- **`--min-instances 0`, `--max-instances 1`**, `512Mi`, 1 vCPU, `--cpu-boost`, public (`--allow-unauthenticated`).
- Every `gcloud` command passes `--project movie-api-511411` explicitly, because a developer's default project may be a different one.

Exact commands and the meaning of every flag: `docs/deployment.md`.

## Consequences
- Data stays in the EU, and latency from Finland is low. Firestore and Cloud Run calls stay inside one region.
- The free tier applies (Cloud Run per billing account; Firestore for the `(default)` Standard database). Hidden costs remain: about 250 MB of Artifact Registry storage per deploy against 0.5 GB free (a cleanup policy is recommended), the `run-sources-*` bucket, and Cloud Build minutes.
- The Firestore location is permanent. Moving region later means a new database (in practice a new project) and moving the data.
- No CI: what runs is whatever working tree was uploaded, so commit before deploying. Rollback is by revision (`gcloud run services update-traffic`).
- Scale to zero costs nothing when idle but gives cold starts of several seconds; one instance limits throughput and keeps one catalog (ADR-0007, ADR-0013).
- The first deploy seeds the sample data once and permanently (ADR-0006); seeding must be checked in the logs straight after the first deploy.
- To change course: add a CI pipeline that calls the same task, raise `min-instances` (costs money), or revisit the storage/search approach above about 500 movies (ADR-0013).

## Update 2026-10-14 (after the first deploy)
- **Artifact Registry cleanup policy applied.** The recommendation in the consequences above was carried out: `config/artifact-registry-cleanup-policy.json` keeps the 2 most recent images in `cloud-run-source-deploy` (the serving revision and one to roll back to) and deletes older ones. The command and the rollback limit are in `docs/deployment.md`.
- **`roles/run.builder` was not needed on this project.** The decision above keeps the least-privilege grant as the recommendation for a fresh project. In this project `gcloud builds get-default-service-account` returned the Compute Engine default account, which already had `roles/editor`, so the grant was skipped and the first deploy (revision `movie-api-00001-w9p`) succeeded without it.

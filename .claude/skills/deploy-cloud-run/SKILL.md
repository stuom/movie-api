---
name: deploy-cloud-run
description: Set up and run one-command deployment of the Movie API to Google Cloud Run (project movie-api-511411) with Gradle, Dockerfile and gcloud, including Firestore database creation and service account setup, cost guardrails and verification with the Postman collection. Use whenever the user mentions deploying, Cloud Run, Google Cloud, GCP, gcloud, Docker, containerizing, going live, service accounts or the deployment document.
---

# Deploy to Google Cloud Run with one command

Goal: after one-time setup, `./gradlew deployCloudRun` runs the quality gate, builds the container in the cloud and deploys it.
All settings live in project files so the deployment is reproducible and reviewable.

Deployment and setup create real, partly billable or permanent cloud resources. Before running any `gcloud` command that creates or changes resources,
show the user the exact command and wait for a clear yes. Never write credentials or key files into the repository.

## Project files that make the one-liner work
Copy and adapt these templates from `templates/`:

| File in repo | Purpose | Template |
|---|---|---|
| `gradle.properties` | Single place for project id, region, service name and runtime service account | `templates/gradle.properties` |
| `build.gradle.kts` | Defines the `deployCloudRun` task (append the snippet) and fixes the jar name to `app.jar` | `templates/deploy-task.gradle.kts` (settings checked when the task runs) |
| `Dockerfile` | Multi-stage build: Gradle `bootJar`, JRE-only non-root runtime | `templates/Dockerfile` |
| `.dockerignore`, `.gcloudignore` | Keep `build/`, `.gradle/`, `.git/`, `.claude/` out of the upload and image | `templates/ignore-file` (copy to both names) |
| `src/main/resources/application.properties` | `server.port=${PORT:8080}`, graceful shutdown, seed switch | `templates/application-cloud.properties` |

`gcloud run deploy --source .` uploads the source, finds the `Dockerfile`, builds it with Cloud Build, stores the image in Artifact Registry and deploys it.
The `deployCloudRun` task depends on `check`, so a failing test, linter or coverage gate blocks the release.

## One-time setup (done by the user in a terminal, not by the agent)
1. Install the Google Cloud CLI and authenticate: `gcloud auth login`.
2. Verify the project id: `gcloud projects describe movie-api-511411` (if it fails, find the real id with `gcloud projects list`; the id differs from the display name).
3. Confirm billing is linked to the project and create a budget alert in the Billing console before the first deploy.
4. Enable APIs:
   `gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com firestore.googleapis.com iam.googleapis.com --project movie-api-511411`
5. Create the Firestore database in Native mode, as the `(default)` database (only that one gets the free tier). **The location cannot be changed later**; use the same region as Cloud Run:
   `gcloud firestore databases create --database="(default)" --location=europe-north1 --type=firestore-native --delete-protection --project movie-api-511411`
   (confirm flags with `gcloud firestore databases create --help`). Check that it is Standard edition: the free tier does not apply to Enterprise.
6. Create the runtime service account and give it only Firestore access:
   ```
   gcloud iam service-accounts create movie-api-run --display-name="Movie API runtime" --project movie-api-511411
   gcloud projects add-iam-policy-binding movie-api-511411 \
     --member="serviceAccount:movie-api-run@movie-api-511411.iam.gserviceaccount.com" --role="roles/datastore.user" --condition=None
   ```
   New IAM bindings take a few minutes to apply; a deploy right after can fail startup with `PERMISSION_DENIED` (wait, then redeploy).
7. Give the Cloud Build service account the right to build and deploy from source (new projects no longer grant it Editor):
   `gcloud builds get-default-service-account --project movie-api-511411` (it prints `projects/<project>/serviceAccounts/<email>`; use only the email), then
   `gcloud projects add-iam-policy-binding movie-api-511411 --member="serviceAccount:<that account>" --role="roles/run.builder" --condition=None`.
   Whoever deploys also needs Service Account User (`iam.serviceAccounts.actAs`) on `movie-api-run`; project owners have it.
8. The first source deploy creates an Artifact Registry repository (`cloud-run-source-deploy`). The task passes `--quiet`, which accepts that default.

## Everyday deployment
```
./gradlew deployCloudRun
```
Equivalent without Gradle (for documentation and debugging):
```
gcloud run deploy movie-api --source . --project movie-api-511411 --region europe-north1 \
  --service-account movie-api-run@movie-api-511411.iam.gserviceaccount.com \
  --allow-unauthenticated --min-instances 0 --max-instances 1 --memory 512Mi --cpu 1 --cpu-boost --quiet
```
Why these flags: `min-instances 0` scales to zero when idle so idle time costs nothing; `max-instances 1` keeps a single in-memory catalog (ADR-0007) so every request sees the same data, and caps cost;
`service-account` runs the app with least privilege instead of the broad default account; `allow-unauthenticated` makes the API publicly reachable;
`512Mi` is enough for a small Spring Boot app (raise to `1Gi` if the container is killed for memory); `cpu-boost` gives extra CPU during startup for shorter cold starts.
`--source .` uploads the working tree, not the last commit: commit before deploying.

## Region and free tier
The region is `europe-north1` (Finland), ADR-0014: close to the users, data in the EU, and a Tier 1 Cloud Run pricing region, so the free tier (applied per billing account) applies. Firestore uses the same region; its location can never be changed.
Firestore's free quota (1 GiB, 50,000 reads, 20,000 writes and 20,000 deletes per day) applies to the single free database per project.
Quotas and prices change: tell the user to check the Cloud Run and Firestore pricing pages, and record the date checked in `docs/deployment.md`.

## Verify after each deploy (Postman)
Use the service URL printed by gcloud (or `gcloud run services describe movie-api --region europe-north1 --project movie-api-511411 --format 'value(status.url)'`).
1. Import `postman/movie-api.postman_collection.json` and `postman/movie-api.postman_environment.json` into Postman and set the environment variable `baseUrl` to the service URL (no trailing slash).
2. Run the whole collection with the Collection Runner. It adds one test movie, lists movies and searches with one field at a time (see `postman/README.md`). All tests should pass.
3. Open `URL/swagger-ui.html` once to check the generated documentation.
Each run adds one test movie to the real database (name starts with `0 Pmtest`); remove leftovers by hand in the Firestore console now and then.
If the list request fails with `PERMISSION_DENIED` or `NOT_FOUND`, see Troubleshooting.

## Troubleshooting
- **Build fails with a permission error** on the first deploy: the account Cloud Build runs as needs build permissions. Grant the Cloud Build service account (`gcloud builds get-default-service-account`) the `roles/run.builder` role (ask the user first), then retry.
- **`PERMISSION_DENIED` from Firestore at runtime:** the runtime service account lacks `roles/datastore.user`, or the service was deployed without `--service-account`.
- **`NOT_FOUND` / database does not exist:** step 5 was skipped, or the database is not `(default)`.
- **`--allow-unauthenticated` is rejected:** an organization policy blocks public services. Report this to the user; do not work around it.
- **Container fails to start / "failed to listen on PORT":** the app must bind to `$PORT`; check `server.port=${PORT:8080}`.
- **Killed for memory:** increase `--memory` or reduce `-XX:MaxRAMPercentage` in the Dockerfile.

## Document in `docs/deployment.md`
Prerequisites, one-time setup, the one-line command, what each setting does and why, the live URL, how to roll back (`gcloud run services update-traffic`),
how to delete everything (`gcloud run services delete movie-api --region europe-north1`; the Firestore database persists separately), and the limitations below.

## Known limitations to state in the docs
- Free quotas are daily; heavy traffic can exhaust them, after which requests fail or billing starts depending on project settings. Budget alerts are the safety net.
- Search runs in memory over a cached catalog (ADR-0007): it suits up to about 500 movies (ADR-0013), has no relevance ranking or typo tolerance, and a catalog reload reads every movie document. Keep `--max-instances 1`.
- The first request after idle has a cold start of several seconds (JVM + Spring startup).
- The service is public and unauthenticated; do not store sensitive data.

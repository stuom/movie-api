# Deployment to Google Cloud Run

The Movie API runs on **Google Cloud Run** and stores movies in **Cloud Firestore**, both in region
**`europe-north1`** (Hamina, Finland) in GCP project **`movie-api-511411`**
([ADR-0014](adr/0014-deploy-to-cloud-run-and-firestore-in-europe-north1.md)).
After the one-time setup below, a deployment is one command: `./gradlew deployCloudRun`.

> **Status:** deployed on 2026-10-14 (first revision `movie-api-00001-w9p`). The one-time cloud setup is done.
> **Live URL:** https://movie-api-719585522889.europe-north1.run.app (Swagger UI: https://movie-api-719585522889.europe-north1.run.app/swagger-ui.html).

All settings come from files in the repository, so every deployment is the same:

| File | What it holds |
|---|---|
| `gradle.properties` | `gcpProjectId`, `gcpRegion`, `cloudRunService`, `cloudRunServiceAccount` |
| `build.gradle.kts` | the `deployCloudRun` task (end of the file) |
| `Dockerfile` | two-stage build: `./gradlew bootJar` on a JDK 21 image, then a JRE 21 image running `app.jar` as the non-root user `appuser` |
| `.gcloudignore`, `.dockerignore` | what is not uploaded or copied into the image (identical lists: `.git`, `build/`, `.claude/`, `docs/`, `*.md`, `config/`, `postman/`, `slice-log/`, `src/test/`, IDE folders) |
| `src/main/resources/application.properties` | `server.port=${PORT:8080}`, graceful shutdown, seed switch |

Change settings in these files, not in ad-hoc commands.

## Prerequisites
- **Google Cloud CLI** (`gcloud`), logged in with `gcloud auth login` as an account that can administer `movie-api-511411`.
- **Java 21 JDK**, as for the build ([README](../README.md#prerequisites)): the deploy runs `./gradlew check` locally first.
- **Billing** linked to the project. Check with `gcloud billing projects describe movie-api-511411`.
- **A budget alert** created in the Billing console before the first deploy. It is the safety net if a free quota is exceeded.

Your local gcloud default project may be a different one. Every command in this document therefore passes
`--project movie-api-511411`; keep it when you copy them.

## One-time setup
Run these once, in this order. They create persistent cloud resources; the Firestore location in step 2
can **never** be changed afterwards.

1. Enable the APIs:
   ```
   gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com firestore.googleapis.com iam.googleapis.com --project movie-api-511411
   ```
2. Create the Firestore database: Native mode, the `(default)` database (only that one gets the free tier),
   in the same region as Cloud Run, protected against deletion:
   ```
   gcloud firestore databases create --database="(default)" --location=europe-north1 --type=firestore-native --delete-protection --project movie-api-511411
   ```
   Then confirm in the console (or with `gcloud firestore databases describe --database="(default)" --project movie-api-511411`)
   that it is **Standard edition**: the free tier does not apply to Enterprise edition.
3. Create the runtime service account and give it only Firestore access (`roles/datastore.user`):
   ```
   gcloud iam service-accounts create movie-api-run --display-name="Movie API runtime" --project movie-api-511411
   gcloud projects add-iam-policy-binding movie-api-511411 --member="serviceAccount:movie-api-run@movie-api-511411.iam.gserviceaccount.com" --role="roles/datastore.user" --condition=None
   ```
   No key file is created or needed: on Cloud Run the app gets credentials for this account through
   Application Default Credentials.
4. Let Cloud Build deploy from source. Find the account Cloud Build runs as, then grant it `roles/run.builder`
   (new projects no longer give it broad rights automatically):
   ```
   gcloud builds get-default-service-account --project movie-api-511411
   gcloud projects add-iam-policy-binding movie-api-511411 --member="serviceAccount:<build account from the previous command>" --role="roles/run.builder" --condition=None
   ```
5. **Wait a few minutes.** New IAM bindings take time to take effect. A deploy straight after step 3 can fail
   startup with `PERMISSION_DENIED`, because the first catalog load fails and that fails startup (ADR-0013).
   If that happens, wait and redeploy.

The first deploy also creates the Artifact Registry repository `cloud-run-source-deploy` and a Cloud Storage
bucket for the uploaded sources (`run-sources-*`); the task's `--quiet` flag accepts that.

## Deploy
Commit first: `--source .` uploads the **working tree**, not the last commit, so uncommitted changes would be
deployed without a record of what is running.

```
./gradlew deployCloudRun
```

The task:
1. runs `./gradlew check` (compile with `-Werror`, format, Checkstyle, SpotBugs, unit tests, coverage); any failure stops the release;
2. checks that the four settings in `gradle.properties` are present, and fails naming any missing key
   (`Missing deployment settings in gradle.properties: ...`). The check runs only when the task runs, so a
   missing key never breaks `check` or `bootJar`;
3. runs `gcloud run deploy` (`gcloud.cmd` on Windows), which uploads the sources, builds the `Dockerfile` with
   Cloud Build, stores the image in Artifact Registry and deploys a new revision.

The equivalent command without Gradle (useful for debugging; it skips `check`):
```
gcloud run deploy movie-api --source . --project movie-api-511411 --region europe-north1 --service-account movie-api-run@movie-api-511411.iam.gserviceaccount.com --allow-unauthenticated --min-instances 0 --max-instances 1 --memory 512Mi --cpu 1 --cpu-boost --quiet
```

### What each flag does and why
| Flag | Why |
|---|---|
| `--source .` | Build in the cloud from the repository; no local Docker needed and no CI pipeline to maintain. |
| `--project movie-api-511411` | Never depend on the local gcloud default project. |
| `--region europe-north1` | Closest region to Finland, in the EU, Tier 1 pricing, same region as Firestore (ADR-0014). |
| `--service-account movie-api-run@...` | Run with least privilege (`roles/datastore.user` only) instead of the broad default compute account. |
| `--allow-unauthenticated` | The API is public: anyone can list, search and add movies (there is no update or delete endpoint, ADR-0006). |
| `--min-instances 0` | Scale to zero when idle, so idle time costs nothing. The price is cold starts. |
| `--max-instances 1` | Each instance has its own in-memory catalog; one instance means every request sees the same data (ADR-0007). It also caps cost. |
| `--memory 512Mi` | Enough for a small Spring Boot app. The Dockerfile limits the heap to 60% (`-XX:MaxRAMPercentage=60`), leaving room for metaspace, thread stacks and the Firestore client's gRPC/Netty buffers. If the container is killed for memory, raise this to `1Gi`. |
| `--cpu 1` | One vCPU, enough for this load. |
| `--cpu-boost` | Extra CPU while the JVM and Spring start: shorter cold starts. |
| `--quiet` | No interactive prompts; accepts defaults such as creating the Artifact Registry repository. |

Related application settings: the app listens on `$PORT` (`server.port=${PORT:8080}`), and on shutdown it lets
in-flight requests finish for up to 8 seconds (`server.shutdown=graceful`,
`spring.lifecycle.timeout-per-shutdown-phase=8s`), because Cloud Run allows about 10 seconds after `SIGTERM`.

## First deploy: check the seed
On the first start the Firestore database is empty, so `MovieSeedLoader` adds the 20 sample movies from
`src/main/resources/seed/movies.json` (ADR-0006). This is a **one-time, permanent write**:
- The sample data is seeded exactly as provided, including the suspected mistakes listed in
  [AI log 06](ai-log/06-catalog-and-seed.md#suspected-seed-data-errors) (for example Avengers: Endgame as 2018).
  There is no update or delete endpoint, so they can only be fixed by hand in the Firestore console.
- Seeding runs after the web server has started listening. Cloud Run only gives an instance CPU while it
  handles a request, so seeding can stall if no request arrives, and an instance stopped part-way leaves a
  partial seed that is never resumed (the catalog is no longer empty on the next start).

So, straight after the first deploy:
1. Send a request so the instance has CPU while it seeds (use the URL printed by the deploy):
   ```
   curl "<url>/api/v1/movies?size=1"
   ```
2. Read the logs and look for `Seeded 20 of 20 sample movies`:
   ```
   gcloud run services logs read movie-api --region europe-north1 --project movie-api-511411
   ```
   A warning `Seeded N of 20 sample movies; ... skipped, ... left after a storage failure` means the seed did not finish.
3. **Recovery** if seeding stopped part-way: delete the partly seeded movie documents in the Firestore console,
   then redeploy (`./gradlew deployCloudRun`). The new instance finds an empty catalog and seeds again.

If you do not want the sample data to come back after deleting every movie, set `app.seed.enabled=false`.

## Verify
Get the URL (the deploy also prints it):
```
gcloud run services describe movie-api --region europe-north1 --project movie-api-511411 --format "value(status.url)"
```
`gcloud run services describe movie-api --region europe-north1 --project movie-api-511411` (without `--format`)
also shows max instances 1 and the runtime service account.

1. The API answers: `curl "<url>/api/v1/movies?size=1"` returns `200` with `"totalItems": 20` after the first seed
   (the first request after idle can take several seconds: cold start).
2. The OpenAPI description: `curl <url>/v3/api-docs`.
3. Swagger UI: open `<url>/swagger-ui.html` in a browser.
4. The Postman collection ([postman/README.md](../postman/README.md)) against the live URL. **Ask first:** each
   run adds one movie named `0 Pmtest <runId>` to the real database, which can only be removed by hand in the
   Firestore console.

## Roll back
Each deploy creates a new revision; the old ones stay available. List them, then send all traffic to a
previous one:
```
gcloud run revisions list --service movie-api --region europe-north1 --project movie-api-511411
gcloud run services update-traffic movie-api --to-revisions=<revision>=100 --region europe-north1 --project movie-api-511411
```
A rollback changes only the code that runs; movies stored in Firestore are not rolled back.
The next `./gradlew deployCloudRun` sends all traffic to the new revision again.

## Delete
```
gcloud run services delete movie-api --region europe-north1 --project movie-api-511411
```
This removes the service only. The Firestore database and its movies persist; the database was created with
delete protection, which must be turned off before it can be deleted. Images in the Artifact Registry
repository `cloud-run-source-deploy` and the uploaded sources in the `run-sources-*` bucket also remain until
deleted separately.

## Costs and free tier
Checked on **2026-10-14** ([Cloud Run locations](https://docs.cloud.google.com/run/docs/locations),
[Cloud Run pricing](https://cloud.google.com/run/pricing),
[Firestore locations](https://docs.cloud.google.com/firestore/native/docs/locations)). Quotas and prices change;
check the pricing pages again before relying on these.

- **Cloud Run:** `europe-north1` is listed under "Subject to Tier 1 pricing", so the Cloud Run free tier applies.
  The free tier is per **billing account**, not per project, so other projects on the same account share it.
  With `--min-instances 0` an idle service costs nothing.
- **Firestore:** the free quota (1 GiB stored, 50,000 reads, 20,000 writes and 20,000 deletes per day) applies to
  the `(default)` Standard edition database only. Every catalog load reads every movie document; keep the
  catalog under about 500 movies (ADR-0013). Seeding costs 20 writes, once.
- **Hidden costs** to watch:
  - **Artifact Registry:** each deploy pushes an image of about 250 MB to `cloud-run-source-deploy`, and the
    free storage is 0.5 GB (the first image was 190 MB). A cleanup policy keeps the **2 most recent images**
    (the serving revision and one to roll back to) and deletes every older one; a Keep rule wins over a
    Delete rule. It was set on 2026-10-14 from `config/artifact-registry-cleanup-policy.json`:
    ```
    gcloud artifacts repositories set-cleanup-policies cloud-run-source-deploy --location=europe-north1 --project=movie-api-511411 --policy=config/artifact-registry-cleanup-policy.json --no-dry-run
    ```
    Cleanup runs in the background, typically within a day. Rolling back further than one revision needs a
    redeploy of an older commit.
  - **Cloud Storage:** the `run-sources-*` bucket keeps every uploaded source archive.
  - **Cloud Build:** build minutes are billed beyond the free allowance. Every build downloads the Gradle
    distribution and all dependencies again (no cache between source builds), which makes builds slower.

## Known limitations
- **Cold starts:** with `--min-instances 0`, the first request after idle waits several seconds for the JVM and
  Spring to start and the catalog to load (`--cpu-boost` shortens this). `min-instances 1` would remove them but
  is not free (ADR-0013).
- **Public and unauthenticated:** anyone can add movies. Do not store sensitive data. Unwanted movies can only be
  removed in the Firestore console.
- **Catalog size:** search runs in memory over a cached catalog (ADR-0007). Every load reads every movie
  document, so the free read quota limits the catalog to about 500 movies (ADR-0013).
- **One instance:** keep `--max-instances 1`. More instances would each hold their own catalog and could show
  different data until their next refresh (ADR-0007). This also limits throughput to what one instance handles.
- **Manual edits in the Firestore console** become visible only after the next catalog reload (at most
  `app.catalog.refresh-interval`, 60 minutes by default) or the next cold start.
- **No CI:** deployments run from a developer machine. Commit before deploying so the running code is traceable.

## Troubleshooting
- **Build fails with a permission error:** the Cloud Build service account lacks `roles/run.builder` (setup step 4).
- **Startup fails with `PERMISSION_DENIED`:** right after setup, wait a few minutes for IAM and redeploy. Otherwise
  the runtime account lacks `roles/datastore.user`, or the service runs without `--service-account`.
- **`NOT_FOUND` / database does not exist:** setup step 2 was skipped, or the database is not `(default)`.
- **`--allow-unauthenticated` is rejected:** an organization policy blocks public services; it has to be changed
  by the organization's administrator.
- **Container killed for memory:** raise `--memory` in `build.gradle.kts`, or lower `-XX:MaxRAMPercentage` in the `Dockerfile`.

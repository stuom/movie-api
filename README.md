# Movie API

REST service to list, search and add movies, built with Spring Boot 3 on Java 21 and deployed to Google Cloud Run.

> Status: early development. The service builds and enforces the quality gates. It needs Firestore credentials to start, so it runs on Cloud Run only; there is no local run by design.
> Implemented and unit tested: adding a movie (`POST /api/v1/movies`), fetching one movie (`GET /api/v1/movies/{id}`), and listing or searching the catalog a page at a time (`GET /api/v1/movies`, at most one search field per request, for example `?genre=sci-fi`, `?minYear=2000&maxYear=2009` or `?actor=harrison%20ford`).
> Deployed to Google Cloud Run in `europe-north1` with one command (`./gradlew deployCloudRun`); see [docs/deployment.md](docs/deployment.md).

## API
- Contract with curl examples and every error response: [docs/api.md](docs/api.md)
- Once deployed, the service serves Swagger UI at `/swagger-ui.html` and the OpenAPI description at `/v3/api-docs` (both public, [ADR-0011](docs/adr/0011-springdoc-openapi.md)).
  Live URL: https://movie-api-719585522889.europe-north1.run.app (Swagger UI: https://movie-api-719585522889.europe-north1.run.app/swagger-ui.html).

## Prerequisites
- **Java 21 JDK**, discoverable by Gradle: either set `JAVA_HOME` to it, or list its path in
  `org.gradle.java.installations.paths` in your user `~/.gradle/gradle.properties`.
- Nothing else: always use the Gradle wrapper (`./gradlew`, or `gradlew.bat` on Windows cmd), never a global `gradle`.

## Build and test
| Task | Command |
|---|---|
| Full quality gate (compiler warnings, format, Checkstyle, SpotBugs, unit tests, coverage) | `./gradlew check` |
| Unit tests only | `./gradlew test` |
| Fix formatting | `./gradlew spotlessApply` |
| Build the runnable jar | `./gradlew bootJar` (output `build/libs/app.jar`) |

Reports are written to `build/reports/` (`checkstyle/`, `spotbugs/`, `jacoco/`, `tests/`).
What each gate enforces and why: [ADR-0008](docs/adr/0008-quality-gates-and-build-verification.md).

## Deploy
After the one-time setup (Firestore database, runtime service account, IAM roles), one command runs the full quality gate and deploys to Cloud Run in `europe-north1` (project `movie-api-511411`):
```
./gradlew deployCloudRun
```
It needs the Google Cloud CLI (`gcloud`) logged in to the project. Commit first: the working tree is uploaded, not the last commit.
Setup, flags, the first-deploy seed check, verification, rollback and costs: [docs/deployment.md](docs/deployment.md) ([ADR-0014](docs/adr/0014-deploy-to-cloud-run-and-firestore-in-europe-north1.md)).

## More
- Project conventions for humans and AI agents: [AGENTS.md](AGENTS.md)
- Architecture decisions: [docs/adr](docs/adr/README.md)
- Firestore data model: [docs/firestore-model.md](docs/firestore-model.md)
- API tests against the deployed service (run by hand, not part of `check`): [postman/README.md](postman/README.md)

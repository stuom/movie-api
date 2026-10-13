# Movie API

REST service to list, search and add movies, built with Spring Boot 3 on Java 21 and deployed to Google Cloud Run.

> Status: early development. The service builds and enforces the quality gates. It needs Firestore credentials to start, so it runs on Cloud Run only; there is no local run by design.
> `POST /api/v1/movies` (add a movie) is implemented and unit tested. Fetching one movie (`GET /api/v1/movies/{id}`), listing and search come in the next slice.
> Do not deploy this state on its own: the `Location` of a new movie points to `GET /api/v1/movies/{id}`, which answers `404` until the next slice is in.
> The Cloud Run deployment setup (`Dockerfile`, `./gradlew deployCloudRun`) has not been added yet.

## API
- Contract with curl examples and every error response: [docs/api.md](docs/api.md)
- Once deployed, the service serves Swagger UI at `/swagger-ui.html` and the OpenAPI description at `/v3/api-docs` (both public, [ADR-0011](docs/adr/0011-springdoc-openapi.md)).
  There is no deployed URL yet.

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

## More
- Project conventions for humans and AI agents: [AGENTS.md](AGENTS.md)
- Architecture decisions: [docs/adr](docs/adr/README.md)
- Firestore data model: [docs/firestore-model.md](docs/firestore-model.md)
- API tests against the deployed service (run by hand, not part of `check`): [postman/README.md](postman/README.md)

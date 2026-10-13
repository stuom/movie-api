# Movie API

REST service to list, search and add movies, built with Spring Boot 3 on Java 21 and deployed to Google Cloud Run.

> Status: early development. The service builds, starts and enforces the quality gates, and the movie domain model
> (validation rules, text normalization, deterministic id) is in place with unit tests. The movie endpoints come in the next slices.

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

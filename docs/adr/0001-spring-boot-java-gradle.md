# ADR-0001: Spring Boot, Java 21 and Gradle

- **Status:** Accepted
- **Date:** 2026-10-12

## Context
The service must be REST-based, written in Java with a modern framework, easy for other developers to pick up, and deployable as a container.

## Options considered
1. **Spring Boot 3 (Spring MVC)**: most widely known Java framework, large ecosystem, first-class OpenAPI and test support. Heavier startup than Quarkus or Micronaut.
2. **Quarkus / Micronaut**: faster startup and lower memory, smaller talent pool and fewer examples.
3. **Maven vs Gradle** as the build tool: both are mainstream. Gradle's Kotlin DSL gives concise custom tasks (for example the one-line deployment) and good incremental builds.

## Decision
Spring Boot 3.x with Spring MVC on Java 21, built with Gradle (Kotlin DSL) and the Gradle wrapper. Versions are declared directly in `build.gradle.kts`: with one build file and few dependencies a version catalog (`gradle/libs.versions.toml`) would add a file without adding clarity, and it can be introduced later if the build grows.

## Consequences
- Familiar conventions help new contributors; Swagger UI comes from springdoc-openapi.
- Cold starts on Cloud Run take several seconds. Acceptable for this service; mitigated by JVM tuning in the `Dockerfile`.
- Build logic lives in `build.gradle.kts`; contributors need basic Gradle knowledge.

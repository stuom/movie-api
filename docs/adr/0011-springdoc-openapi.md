# ADR-0011: OpenAPI description and Swagger UI with springdoc-openapi

- **Status:** Accepted
- **Date:** 2026-10-13

## Context
AGENTS.md requires every endpoint to be annotated for OpenAPI (summary, parameters, response codes) and a browsable Swagger UI on the deployed service. The project runs on Spring Boot 3.5 with Spring MVC. Any new dependency needs the user's approval and an ADR. Two problems turned up while planning slice 04:
- Every operation would be documented with every response that the `@RestControllerAdvice` can produce (about ten generic ones), which hides the responses that really apply.
- The generated `ProblemDetail` schema cannot show our extension members (`violations`, `movieId`), because Jackson writes them as top-level fields of the problem.

## Options considered
1. **springdoc-openapi** (`springdoc-openapi-starter-webmvc-ui`): generates the OpenAPI document from Spring MVC mappings and swagger annotations, and serves Swagger UI. It is the common choice for Spring Boot 3. Costs: one more dependency tree and annotations on the controller and DTOs.
2. **Hand-written OpenAPI YAML**: no dependency, but the file drifts from the code because nothing checks it.
3. **No OpenAPI, only `docs/api.md`**: breaks the AGENTS.md requirement, and clients get no machine-readable contract.

## Decision
Use `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17`, declared in `build.gradle.kts` and approved by the user with the slice 04 plan. The 2.8.x line is the one for Spring Boot 3.5; springdoc 3.x targets Spring Boot 4.
- **Annotations:** `@Operation`, `@ApiResponse`, `@Tag` on `MovieController`, `@Schema` on the DTO records (`NewMovieRequest`, `MovieResponse`, `PersonDto`), and API-wide information in `common/OpenApiConfig`.
- **Only declared responses:** `springdoc.override-with-generic-response=false` in `application.properties`, so each operation shows only the responses declared on it.
- **Error responses:** documented as `application/problem+json` with `schema = ProblemDetail` plus `@ExampleObject`s that mirror `docs/api.md`. The examples are what shows the flattened extensions.
- **Limits from one source:** the `@Schema` limits (`maxLength`, `minimum`, `maxItems`, ...) refer to the public constants in `MovieRules` (ADR-0009), so the documented limits cannot drift from the enforced ones. The year ceiling (current year + 5) changes over time, so it appears only in the description text.
- **Public endpoints:** the OpenAPI document (`/v3/api-docs`) and Swagger UI (`/swagger-ui.html`) are served without authentication, like the API itself.

## Consequences
- The machine-readable contract is generated from the code, and Swagger UI lets a reader try the API in the browser.
- `docs/api.md` stays the human-readable contract. The error examples now exist twice (in `docs/api.md` and in the `@ExampleObject`s) and must be updated together.
- Adding an endpoint means listing each of its responses explicitly, because no generic responses are added.
- The OpenAPI output has no automated test (there is no local run, by design). It is checked by hand in Swagger UI after a deploy.
- Moving to Spring Boot 4 also means moving to springdoc 3.x.

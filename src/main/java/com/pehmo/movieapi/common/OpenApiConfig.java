package com.pehmo.movieapi.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API as a whole in the generated OpenAPI document ({@code /v3/api-docs}, Swagger UI
 * at {@code /swagger-ui.html}). Each endpoint documents itself with annotations on its controller;
 * {@code docs/api.md} is the human-readable contract (ADR-0011).
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Movie API",
            version = "v1",
            description =
                "List, search and add movies. Errors are RFC 7807 problem details"
                    + " (application/problem+json). There is no update or delete endpoint."))
public class OpenApiConfig {}

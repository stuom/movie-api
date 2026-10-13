package com.pehmo.movieapi.movie.web;

import com.pehmo.movieapi.movie.service.MovieRules;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A director or actor in a request or response body. In a request either name may be missing; the
 * rules are checked by {@code MovieRules}, and the schema limits only document them (ADR-0009).
 *
 * @param firstName the first name
 * @param lastName the last name; multi-word last names such as "Lee Jones" are normal
 */
@Schema(description = "A director or actor.")
public record PersonDto(
    @Schema(
            description = "First name, trimmed.",
            example = "Sigourney",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = MovieRules.MAX_PERSON_NAME_LENGTH)
        String firstName,
    @Schema(
            description = "Last name, trimmed. Multi-word last names are normal.",
            example = "Weaver",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = MovieRules.MAX_PERSON_NAME_LENGTH)
        String lastName) {}

package com.pehmo.movieapi.movie.web;

import com.pehmo.movieapi.movie.service.MovieRules;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The body of {@code POST /api/v1/movies}, exactly as received.
 *
 * <p>Every field is nullable and there are no Bean Validation constraints: {@code MovieRules}
 * checks the movie and reports every violation with its field path (ADR-0009). The schema limits
 * refer to the {@link MovieRules} constants, so the documentation cannot drift from the enforced
 * rules. There is no {@code id}: it is derived from name and year, and sending one is an unknown
 * field.
 *
 * @param name the name
 * @param year the release year
 * @param genres the genres
 * @param ageLimit the minimum recommended viewer age in years
 * @param rating the rating in stars
 * @param director the director, or null when unknown
 * @param actors the actors in billing order, or null when none are known
 * @param synopsis the synopsis, or null
 */
@Schema(description = "A movie to add. Text is trimmed; unknown fields are rejected.")
public record NewMovieRequest(
    @Schema(
            description = "Name of the movie, trimmed; no control characters.",
            example = "Aliens",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = MovieRules.MAX_NAME_LENGTH)
        String name,
    @Schema(
            description = "Release year, from 1888 to the current year + 5.",
            example = "1986",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "" + MovieRules.FIRST_FILM_YEAR)
        Integer year,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description = "Genres, free text, trimmed; no control characters.",
                    example = "[\"Action\", \"Sci-fi\"]",
                    requiredMode = Schema.RequiredMode.REQUIRED),
            minItems = MovieRules.MIN_GENRES,
            maxItems = MovieRules.MAX_GENRES,
            schema = @Schema(minLength = 1, maxLength = MovieRules.MAX_GENRE_LENGTH))
        List<String> genres,
    @Schema(
            description = "Minimum recommended viewer age in years.",
            example = "16",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "" + MovieRules.MIN_AGE_LIMIT,
            maximum = "" + MovieRules.MAX_AGE_LIMIT)
        Integer ageLimit,
    @Schema(
            description = "Rating in stars.",
            example = "5",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "" + MovieRules.MIN_RATING,
            maximum = "" + MovieRules.MAX_RATING)
        Integer rating,
    @Schema(description = "The director; omit or send null when unknown.", nullable = true)
        PersonDto director,
    @ArraySchema(
            arraySchema = @Schema(description = "Actors in billing order; optional."),
            maxItems = MovieRules.MAX_ACTORS)
        List<PersonDto> actors,
    @Schema(
            description = "Synopsis; optional. A blank synopsis counts as none.",
            example = "Ellen Ripley is rescued by a deep salvage team.",
            nullable = true,
            maxLength = MovieRules.MAX_SYNOPSIS_LENGTH)
        String synopsis) {

  /** Copies the lists while keeping null lists and null entries for {@code MovieRules}. */
  public NewMovieRequest {
    // Not List.copyOf: it rejects null entries, which must reach MovieRules to be reported.
    genres = genres == null ? null : Collections.unmodifiableList(new ArrayList<>(genres));
    actors = actors == null ? null : Collections.unmodifiableList(new ArrayList<>(actors));
  }
}

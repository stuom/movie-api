package com.pehmo.movieapi.movie.web;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A movie as the API returns it. Every field is always present, so clients see one stable shape: an
 * unknown director or synopsis is {@code null} and missing actors are an empty list.
 *
 * @param id the deterministic id derived from the normalized name and the year
 * @param name the trimmed name
 * @param year the release year
 * @param genres the trimmed genres in the order given
 * @param ageLimit the minimum recommended viewer age in years
 * @param rating the rating in stars
 * @param director the director, or null when unknown
 * @param actors the actors in billing order, empty when none are known
 * @param synopsis the synopsis, or null when there is none
 */
@Schema(description = "A stored movie.")
public record MovieResponse(
    @Schema(
            description = "Id derived from the normalized name and the year (32 hex characters).",
            example = "53e87be920fdef6e6176fa0c355406d8",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
    @Schema(example = "Aliens", requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(example = "1986", requiredMode = Schema.RequiredMode.REQUIRED) int year,
    @ArraySchema(
            arraySchema =
                @Schema(
                    example = "[\"Action\", \"Sci-fi\"]",
                    requiredMode = Schema.RequiredMode.REQUIRED))
        List<String> genres,
    @Schema(example = "16", requiredMode = Schema.RequiredMode.REQUIRED) int ageLimit,
    @Schema(example = "5", requiredMode = Schema.RequiredMode.REQUIRED) int rating,
    @Schema(
            description = "The director, or null when unknown.",
            nullable = true,
            requiredMode = Schema.RequiredMode.REQUIRED)
        PersonDto director,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description = "Actors in billing order; empty when none are known.",
                    requiredMode = Schema.RequiredMode.REQUIRED))
        List<PersonDto> actors,
    @Schema(
            description = "The synopsis, or null when there is none.",
            example = "Ellen Ripley is rescued by a deep salvage team.",
            nullable = true,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String synopsis) {

  /** Freezes the lists, so a response cannot be changed after it was built. */
  public MovieResponse {
    genres = List.copyOf(genres);
    actors = List.copyOf(actors);
  }
}

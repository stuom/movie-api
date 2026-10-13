package com.pehmo.movieapi.movie.web;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One page of movies as the API returns it, with the counts a client needs to page through all of
 * them.
 *
 * @param items the movies on this page, sorted by name ignoring case and then id
 * @param page the 0-based page number returned
 * @param size the page size used
 * @param totalItems the number of movies on all pages together
 * @param totalPages the number of pages, 0 when there are no movies
 */
@Schema(description = "One page of movies.")
public record MoviePageResponse(
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "The movies on this page, at most size; empty past the last page.",
                    requiredMode = Schema.RequiredMode.REQUIRED))
        List<MovieResponse> items,
    @Schema(
            description = "The 0-based page number returned.",
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
    @Schema(
            description = "The page size used.",
            example = "20",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
    @Schema(
            description = "The number of movies on all pages together.",
            example = "57",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int totalItems,
    @Schema(
            description = "ceil(totalItems / size); 0 when there are no movies.",
            example = "3",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages) {

  /** Freezes the items, so a response cannot be changed after it was built. */
  public MoviePageResponse {
    items = List.copyOf(items);
  }
}

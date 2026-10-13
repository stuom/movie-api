package com.pehmo.movieapi.movie.web;

import com.pehmo.movieapi.movie.service.Movie;
import com.pehmo.movieapi.movie.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The movie resource. It only maps HTTP to DTOs and back and delegates to {@link MovieService};
 * errors become problem details in {@code common.ApiExceptionHandler}. The full contract, with
 * examples, is in {@code docs/api.md}.
 */
@RestController
@RequestMapping(MovieController.BASE_PATH)
@Tag(name = "Movies", description = "List, search and add movies.")
public class MovieController {

  /** Base path of the movie resource; also the base of the {@code Location} of a new movie. */
  static final String BASE_PATH = "/api/v1/movies";

  private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

  private final MovieService movieService;

  /** Creates the controller on the movie use cases. */
  public MovieController(MovieService movieService) {
    this.movieService = movieService;
  }

  /**
   * Adds a movie. The {@code Location} is a relative path: valid per RFC 9110, and correct behind
   * Cloud Run's proxy without trusting forwarded headers.
   */
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Add a movie",
      description =
          "Validates and stores a movie. Text is trimmed, types are strict and unknown fields are"
              + " rejected. The id is derived from the normalized name and the year, so the same"
              + " film cannot be added twice. Every broken rule is reported at once.")
  @ApiResponse(
      responseCode = "201",
      description = "Movie added; the body is the movie as stored.",
      headers =
          @Header(
              name = "Location",
              description = "Relative path of the new movie, /api/v1/movies/{id}.",
              schema = @Schema(type = "string")))
  @ApiResponse(
      responseCode = "400",
      description = "The movie breaks rules, or the body cannot be read as a movie.",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class),
              examples = {
                @ExampleObject(
                    name = "rules broken",
                    value =
                        """
                        {"type": "about:blank", "title": "Bad Request", "status": 400,
                         "detail": "The movie breaks 2 rules; see violations.",
                         "instance": "/api/v1/movies",
                         "violations": [{"field": "name", "message": "is required"},
                                        {"field": "actors[0].lastName", "message": "is required"}]}
                        """),
                @ExampleObject(
                    name = "unreadable body",
                    value =
                        """
                        {"type": "about:blank", "title": "Bad Request", "status": 400,
                         "detail": "The request body is not a valid movie: it must be a JSON \
                        object with the documented fields and types.",
                         "instance": "/api/v1/movies",
                         "violations": [{"field": "year", "message": "has the wrong type"}]}
                        """)
              }))
  @ApiResponse(
      responseCode = "409",
      description = "A movie with the same normalized name and year already exists.",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class),
              examples =
                  @ExampleObject(
                      value =
                          """
                          {"type": "about:blank", "title": "Conflict", "status": 409,
                           "detail": "A movie with the same name and year as 'aliens' (1986) \
                          already exists.",
                           "instance": "/api/v1/movies",
                           "movieId": "53e87be920fdef6e6176fa0c355406d8"}
                          """)))
  @ApiResponse(
      responseCode = "413",
      description = "The body is larger than 256 KB.",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "415",
      description = "The Content-Type is not application/json.",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "500",
      description =
          "The store failed or did not answer in time (the movie may have been saved; retrying"
              + " is safe), or an unexpected error occurred.",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<MovieResponse> createMovie(@RequestBody NewMovieRequest request) {
    Movie movie = movieService.createMovie(MovieMapper.toNewMovie(request));
    URI location = UriComponentsBuilder.fromPath(BASE_PATH).path("/{id}").build(movie.id());
    return ResponseEntity.created(location).body(MovieMapper.toResponse(movie));
  }
}

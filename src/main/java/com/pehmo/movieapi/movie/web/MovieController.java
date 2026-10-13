package com.pehmo.movieapi.movie.web;

import com.pehmo.movieapi.movie.service.Movie;
import com.pehmo.movieapi.movie.service.MoviePage;
import com.pehmo.movieapi.movie.service.MovieQuery;
import com.pehmo.movieapi.movie.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  /** Returns one movie from the in-memory catalog. */
  @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Fetch one movie",
      description =
          "Returns the movie with this id from the catalog, the same copy that listing reads.")
  @ApiResponse(responseCode = "200", description = "The movie.")
  @ApiResponse(
      responseCode = "404",
      description = "No movie has this id.",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class),
              examples =
                  @ExampleObject(
                      value =
                          """
                          {"type": "about:blank", "title": "Not Found", "status": 404,
                           "detail": "There is no movie with this id.",
                           "instance": "/api/v1/movies/0123456789abcdef0123456789abcdef"}
                          """)))
  @ApiResponse(
      responseCode = "500",
      description = "An unexpected error occurred.",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  public MovieResponse getMovie(
      @Parameter(
              description =
                  "The movie id. Ids are 32 lowercase hex characters; any other text is a 404.",
              example = "53e87be920fdef6e6176fa0c355406d8")
          @PathVariable
          String id) {
    return MovieMapper.toResponse(movieService.findMovie(id));
  }

  /**
   * Lists the catalog one page at a time. All parameters arrive as one raw map, so {@code
   * MovieQuery} can reject unknown and repeated ones and report every problem at once; the
   * documented parameters are therefore declared on the operation.
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List movies",
      description =
          "Returns the whole catalog a page at a time, sorted by name ignoring case and then by"
              + " id. Out-of-range values are rejected, not clamped, and so are unknown"
              + " parameters. A page past the end is empty and keeps the true totals.",
      parameters = {
        @Parameter(
            name = "page",
            in = ParameterIn.QUERY,
            description = "0-based page number.",
            schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")),
        @Parameter(
            name = "size",
            in = ParameterIn.QUERY,
            description = "Movies per page.",
            schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
      })
  @ApiResponse(responseCode = "200", description = "The requested page.")
  @ApiResponse(
      responseCode = "400",
      description =
          "A parameter is out of range, not a whole number, given more than once, or unknown.",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class),
              examples =
                  @ExampleObject(
                      value =
                          """
                          {"type": "about:blank", "title": "Bad Request", "status": 400,
                           "detail": "The query parameters are invalid; see violations.",
                           "instance": "/api/v1/movies",
                           "violations": [{"field": "size", "message": "must be between 1 and 100"},
                                          {"field": "pgae", "message": "is not a known parameter"}]}
                          """)))
  @ApiResponse(
      responseCode = "500",
      description = "An unexpected error occurred.",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  public MoviePageResponse listMovies(
      @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
    MoviePage page = movieService.listMovies(MovieQuery.parse(parameters));
    return MovieMapper.toPageResponse(page);
  }
}

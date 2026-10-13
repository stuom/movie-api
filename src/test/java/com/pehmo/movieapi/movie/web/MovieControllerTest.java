package com.pehmo.movieapi.movie.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pehmo.movieapi.common.ApiExceptionHandler;
import com.pehmo.movieapi.common.JacksonConfig;
import com.pehmo.movieapi.movie.service.DuplicateMovieException;
import com.pehmo.movieapi.movie.service.FieldViolation;
import com.pehmo.movieapi.movie.service.InvalidMovieException;
import com.pehmo.movieapi.movie.service.Movie;
import com.pehmo.movieapi.movie.service.MovieNotFoundException;
import com.pehmo.movieapi.movie.service.MoviePage;
import com.pehmo.movieapi.movie.service.MoviePageRequest;
import com.pehmo.movieapi.movie.service.MovieQuery;
import com.pehmo.movieapi.movie.service.MovieService;
import com.pehmo.movieapi.movie.service.MovieStorageException;
import com.pehmo.movieapi.movie.service.MovieTestData;
import com.pehmo.movieapi.movie.service.NewMovie;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(MovieController.class)
// ApiExceptionHandler is already part of the slice and is imported for readability. JacksonConfig
// is a user @Configuration, which @WebMvcTest leaves out, but production reads bodies with it.
@Import({ApiExceptionHandler.class, JacksonConfig.class})
class MovieControllerTest {

  private static final String PATH = "/api/v1/movies";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String UNREADABLE_BODY_DETAIL =
      "The request body is not a valid movie: it must be a JSON object with the documented"
          + " fields and types.";
  private static final String STORAGE_FAILURE_DETAIL =
      "The movie store failed or did not answer in time. If you were adding a movie, it may have"
          + " been saved; retrying is safe.";
  private static final String UNEXPECTED_DETAIL = "An unexpected error occurred.";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private MovieService movieService;

  /** The request body equivalent to {@code MovieTestData.aNewMovie().build()}. */
  private static Map<String, Object> aValidBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Aliens");
    body.put("year", 1986);
    body.put("genres", List.of("Action", "Sci-fi"));
    body.put("ageLimit", 16);
    body.put("rating", 5);
    body.put("director", Map.of("firstName", "James", "lastName", "Cameron"));
    body.put("actors", List.of(Map.of("firstName", "Sigourney", "lastName", "Weaver")));
    body.put("synopsis", "Ellen Ripley is rescued by a deep salvage team.");
    return body;
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private ResultActions postJson(String body) throws Exception {
    return mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions postJson(Map<String, Object> body) throws Exception {
    return postJson(json(body));
  }

  /** Asserts the members every problem detail has, except {@code detail}. */
  private static ResultActions expectProblem(
      ResultActions result, HttpStatus expected, String instance) throws Exception {
    return result
        .andExpect(status().is(expected.value()))
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value(expected.getReasonPhrase()))
        .andExpect(jsonPath("$.status").value(expected.value()))
        .andExpect(jsonPath("$.instance").value(instance));
  }

  private static ResultActions expectProblem(
      ResultActions result, HttpStatus expected, String detail, String instance) throws Exception {
    return expectProblem(result, expected, instance).andExpect(jsonPath("$.detail").value(detail));
  }

  @Nested
  class Fetched {

    @Test
    void shouldReturn200WithFullBodyWhenMovieExists() throws Exception {
      // given
      Movie movie = MovieTestData.aMovie().buildMovie();
      given(movieService.findMovie(movie.id())).willReturn(movie);

      // when
      ResultActions result = mockMvc.perform(get(PATH + "/" + movie.id()));

      // then
      result
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.id").value(movie.id()))
          .andExpect(jsonPath("$.name").value("Aliens"))
          .andExpect(jsonPath("$.year").value(1986))
          .andExpect(jsonPath("$.genres[0]").value("Action"))
          .andExpect(jsonPath("$.genres[1]").value("Sci-fi"))
          .andExpect(jsonPath("$.ageLimit").value(16))
          .andExpect(jsonPath("$.rating").value(5))
          .andExpect(jsonPath("$.director.firstName").value("James"))
          .andExpect(jsonPath("$.director.lastName").value("Cameron"))
          .andExpect(jsonPath("$.actors[0].firstName").value("Sigourney"))
          .andExpect(jsonPath("$.actors[0].lastName").value("Weaver"))
          .andExpect(
              jsonPath("$.synopsis").value("Ellen Ripley is rescued by a deep salvage team."));
    }

    @Test
    void shouldReturn404ProblemWithoutEchoingIdWhenMovieDoesNotExist() throws Exception {
      // given
      String id = "no-such-movie-id";
      given(movieService.findMovie(id)).willThrow(new MovieNotFoundException(id));

      // when
      ResultActions result = mockMvc.perform(get(PATH + "/" + id));

      // then
      expectProblem(
              result, HttpStatus.NOT_FOUND, "There is no movie with this id.", PATH + "/" + id)
          .andExpect(jsonPath("$.detail").value(not(containsString(id))))
          .andExpect(jsonPath("$.title").value(not(containsString(id))));
    }
  }

  @Nested
  class Listed {

    private static final String LIST_PROBLEM_DETAIL =
        "The query parameters are invalid; see violations.";

    @Test
    void shouldPassDefaultPagingToServiceAndReturnPageShapeWhenNoParametersAreGiven()
        throws Exception {
      // given
      Movie movie = MovieTestData.aMovie().buildMovie();
      given(movieService.listMovies(any(MovieQuery.class)))
          .willReturn(new MoviePage(List.of(movie), 0, 20, 1, 1));

      // when
      ResultActions result = mockMvc.perform(get(PATH));

      // then
      verify(movieService).listMovies(new MovieQuery(new MoviePageRequest(0, 20)));
      result
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].id").value(movie.id()))
          .andExpect(jsonPath("$.items[0].name").value("Aliens"))
          .andExpect(jsonPath("$.page").value(0))
          .andExpect(jsonPath("$.size").value(20))
          .andExpect(jsonPath("$.totalItems").value(1))
          .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void shouldPassPageAndSizeToServiceWhenBothAreGiven() throws Exception {
      // given
      given(movieService.listMovies(any(MovieQuery.class)))
          .willReturn(new MoviePage(List.of(), 1, 10, 0, 0));

      // when
      ResultActions result = mockMvc.perform(get(PATH).param("page", "1").param("size", "10"));

      // then
      result.andExpect(status().isOk());
      verify(movieService).listMovies(new MovieQuery(new MoviePageRequest(1, 10)));
    }

    @Test
    void shouldReturnEmptyItemsArrayWhenPageHasNoMovies() throws Exception {
      // given
      given(movieService.listMovies(any(MovieQuery.class)))
          .willReturn(new MoviePage(List.of(), 0, 20, 0, 0));

      // when
      ResultActions result = mockMvc.perform(get(PATH));

      // then
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items").isArray())
          .andExpect(jsonPath("$.items").isEmpty())
          .andExpect(jsonPath("$.page").value(0))
          .andExpect(jsonPath("$.size").value(20))
          .andExpect(jsonPath("$.totalItems").value(0))
          .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void shouldReturn400WithViolationAndNotCallServiceWhenSizeIsAboveMaximum() throws Exception {
      // given
      MockHttpServletRequestBuilder request = get(PATH).param("size", "101");

      // when
      ResultActions result = mockMvc.perform(request);

      // then
      expectProblem(result, HttpStatus.BAD_REQUEST, LIST_PROBLEM_DETAIL, PATH)
          .andExpect(jsonPath("$.violations.length()").value(1))
          .andExpect(jsonPath("$.violations[0].field").value("size"))
          .andExpect(jsonPath("$.violations[0].message").value("must be between 1 and 100"));
      verifyNoInteractions(movieService);
    }

    @Test
    void shouldReturn400WithoutEchoingValueWhenPageIsNotWholeNumber() throws Exception {
      // given
      MockHttpServletRequestBuilder request = get(PATH).param("page", "abc");

      // when
      ResultActions result = mockMvc.perform(request);

      // then
      expectProblem(result, HttpStatus.BAD_REQUEST, LIST_PROBLEM_DETAIL, PATH)
          .andExpect(jsonPath("$.violations[0].field").value("page"))
          .andExpect(jsonPath("$.violations[0].message").value("must be a whole number"))
          .andExpect(content().string(not(containsString("abc"))));
      verifyNoInteractions(movieService);
    }

    @Test
    void shouldReturn400WhenParameterNameIsUnknown() throws Exception {
      // given
      MockHttpServletRequestBuilder request = get(PATH).param("pgae", "2");

      // when
      ResultActions result = mockMvc.perform(request);

      // then
      expectProblem(result, HttpStatus.BAD_REQUEST, LIST_PROBLEM_DETAIL, PATH)
          .andExpect(jsonPath("$.violations.length()").value(1))
          .andExpect(jsonPath("$.violations[0].field").value("pgae"))
          .andExpect(jsonPath("$.violations[0].message").value("is not a known parameter"));
      verifyNoInteractions(movieService);
    }

    @Test
    void shouldReturn400WhenParameterIsRepeated() throws Exception {
      // given
      MockHttpServletRequestBuilder request = get(PATH).param("page", "1", "2");

      // when
      ResultActions result = mockMvc.perform(request);

      // then
      expectProblem(result, HttpStatus.BAD_REQUEST, LIST_PROBLEM_DETAIL, PATH)
          .andExpect(jsonPath("$.violations[0].field").value("page"))
          .andExpect(jsonPath("$.violations[0].message").value("must be given only once"));
      verifyNoInteractions(movieService);
    }

    @Test
    void shouldReturnAllViolationsInOrderWhenSeveralParametersAreInvalid() throws Exception {
      // given
      MockHttpServletRequestBuilder request =
          get(PATH).param("size", "0").param("page", "-1").param("genre", "drama");

      // when
      ResultActions result = mockMvc.perform(request);

      // then
      expectProblem(result, HttpStatus.BAD_REQUEST, LIST_PROBLEM_DETAIL, PATH)
          .andExpect(jsonPath("$.violations.length()").value(3))
          .andExpect(jsonPath("$.violations[0].field").value("page"))
          .andExpect(jsonPath("$.violations[1].field").value("size"))
          .andExpect(jsonPath("$.violations[2].field").value("genre"));
      verifyNoInteractions(movieService);
    }
  }

  @Nested
  class Created {

    @Test
    void shouldReturn201WithRelativeLocationAndFullBodyWhenMovieIsValid() throws Exception {
      // given
      Movie created = MovieTestData.aMovie().buildMovie();
      given(movieService.createMovie(any(NewMovie.class))).willReturn(created);

      // when
      ResultActions result = postJson(aValidBody());

      // then
      result
          .andExpect(status().isCreated())
          .andExpect(header().string("Location", PATH + "/" + created.id()))
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.id").value(created.id()))
          .andExpect(jsonPath("$.name").value("Aliens"))
          .andExpect(jsonPath("$.year").value(1986))
          .andExpect(jsonPath("$.genres[0]").value("Action"))
          .andExpect(jsonPath("$.genres[1]").value("Sci-fi"))
          .andExpect(jsonPath("$.ageLimit").value(16))
          .andExpect(jsonPath("$.rating").value(5))
          .andExpect(jsonPath("$.director.firstName").value("James"))
          .andExpect(jsonPath("$.director.lastName").value("Cameron"))
          .andExpect(jsonPath("$.actors[0].firstName").value("Sigourney"))
          .andExpect(jsonPath("$.actors[0].lastName").value("Weaver"))
          .andExpect(
              jsonPath("$.synopsis").value("Ellen Ripley is rescued by a deep salvage team."));
    }

    @Test
    void shouldReturnNullDirectorNullSynopsisAndEmptyActorsWhenOptionalFieldsAreAbsent()
        throws Exception {
      // given
      Movie created =
          MovieTestData.aMovie()
              .withDirector(null)
              .withActors(null)
              .withSynopsis(null)
              .buildMovie();
      given(movieService.createMovie(any(NewMovie.class))).willReturn(created);
      Map<String, Object> body = aValidBody();
      body.remove("director");
      body.remove("actors");
      body.remove("synopsis");

      // when
      ResultActions result = postJson(body);

      // then
      result
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$", hasKey("director")))
          .andExpect(jsonPath("$.director").value(nullValue()))
          .andExpect(jsonPath("$", hasKey("synopsis")))
          .andExpect(jsonPath("$.synopsis").value(nullValue()))
          .andExpect(jsonPath("$", hasKey("actors")))
          .andExpect(jsonPath("$.actors").isEmpty());
    }

    @Test
    void shouldPassRequestToServiceAsNewMovieWhenMovieIsValid() throws Exception {
      // given
      given(movieService.createMovie(any(NewMovie.class)))
          .willReturn(MovieTestData.aMovie().buildMovie());

      // when
      postJson(aValidBody());

      // then
      ArgumentCaptor<NewMovie> received = ArgumentCaptor.forClass(NewMovie.class);
      verify(movieService).createMovie(received.capture());
      assertThat(received.getValue()).isEqualTo(MovieTestData.aNewMovie().build());
    }

    @Test
    void shouldPassNullEntriesToServiceWhenListsContainNulls() throws Exception {
      // given
      given(movieService.createMovie(any(NewMovie.class)))
          .willReturn(MovieTestData.aMovie().buildMovie());
      Map<String, Object> body = aValidBody();
      body.put("genres", Arrays.asList("Action", null));
      body.put("actors", Arrays.asList(null, Map.of("firstName", "A", "lastName", "B")));

      // when
      postJson(body);

      // then
      ArgumentCaptor<NewMovie> received = ArgumentCaptor.forClass(NewMovie.class);
      verify(movieService).createMovie(received.capture());
      assertThat(received.getValue().genres()).containsExactly("Action", null);
      assertThat(received.getValue().actors())
          .containsExactly(null, new NewMovie.PersonInput("A", "B"));
    }
  }

  @Nested
  class RulesBroken {

    @Test
    void shouldReturn400WithAllViolationsInOrderWhenMovieBreaksRules() throws Exception {
      // given
      given(movieService.createMovie(any(NewMovie.class)))
          .willThrow(
              new InvalidMovieException(
                  List.of(
                      new FieldViolation("name", "is required"),
                      new FieldViolation("rating", "must be between 1 and 5"),
                      new FieldViolation("actors[0].lastName", "is required"))));

      // when
      ResultActions result = postJson(aValidBody());

      // then
      expectProblem(
              result, HttpStatus.BAD_REQUEST, "The movie breaks 3 rules; see violations.", PATH)
          .andExpect(jsonPath("$.violations.length()").value(3))
          .andExpect(jsonPath("$.violations[0].field").value("name"))
          .andExpect(jsonPath("$.violations[0].message").value("is required"))
          .andExpect(jsonPath("$.violations[1].field").value("rating"))
          .andExpect(jsonPath("$.violations[1].message").value("must be between 1 and 5"))
          .andExpect(jsonPath("$.violations[2].field").value("actors[0].lastName"))
          .andExpect(jsonPath("$.violations[2].message").value("is required"));
    }

    @Test
    void shouldCountRulesInDetailWhenOnlyTwoRulesAreBroken() throws Exception {
      // given
      given(movieService.createMovie(any(NewMovie.class)))
          .willThrow(
              new InvalidMovieException(
                  List.of(
                      new FieldViolation("year", "must be between 1888 and 2031"),
                      new FieldViolation("genres", "must have 1 to 5 entries"))));

      // when
      ResultActions result = postJson(aValidBody());

      // then
      expectProblem(
              result, HttpStatus.BAD_REQUEST, "The movie breaks 2 rules; see violations.", PATH)
          .andExpect(jsonPath("$.violations.length()").value(2));
    }

    @Test
    void shouldUseSingularInDetailWhenOneRuleIsBroken() throws Exception {
      // given
      given(movieService.createMovie(any(NewMovie.class)))
          .willThrow(new InvalidMovieException(List.of(new FieldViolation("name", "is required"))));

      // when
      ResultActions result = postJson(aValidBody());

      // then
      expectProblem(
              result, HttpStatus.BAD_REQUEST, "The movie breaks 1 rule; see violations.", PATH)
          .andExpect(jsonPath("$.violations.length()").value(1));
    }
  }

  @Nested
  class UnreadableBody {

    private void assertUnreadableAndServiceNotCalled(ResultActions result) throws Exception {
      expectProblem(result, HttpStatus.BAD_REQUEST, UNREADABLE_BODY_DETAIL, PATH);
      verifyNoInteractions(movieService);
    }

    @Test
    void shouldReturn400WithoutViolationsWhenBodyIsNotJson() throws Exception {
      // given
      String body = "{\"name\": ";

      // when
      ResultActions result = postJson(body);

      // then
      assertUnreadableAndServiceNotCalled(result);
      result.andExpect(jsonPath("$.violations").doesNotExist());
    }

    @Test
    void shouldReturn400WithoutViolationsWhenBodyIsMissing() throws Exception {
      // given a request without any body

      // when
      ResultActions result = mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON));

      // then
      assertUnreadableAndServiceNotCalled(result);
      result.andExpect(jsonPath("$.violations").doesNotExist());
    }

    @Test
    void shouldReturn400WithoutViolationsWhenContentFollowsTheJsonObject() throws Exception {
      // given
      String body = json(aValidBody()) + " garbage";

      // when
      ResultActions result = postJson(body);

      // then
      assertUnreadableAndServiceNotCalled(result);
      result.andExpect(jsonPath("$.violations").doesNotExist());
    }

    static Stream<String> bodiesWithoutFieldToBlame() {
      return Stream.of("[]", "{\"name\": \"A\", \"name\": \"B\"}", "{\"name\": \"Aliens\"} {}");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodiesWithoutFieldToBlame")
    void shouldReturn400WithoutViolationsWhenNoSingleFieldIsToBlame(String body) throws Exception {
      // given a JSON array, a repeated key, or a second JSON value after the object

      // when
      ResultActions result = postJson(body);

      // then
      assertUnreadableAndServiceNotCalled(result);
      result.andExpect(jsonPath("$.violations").doesNotExist());
    }

    static Stream<Arguments> wrongTypes() {
      return Stream.of(
          Arguments.of("year", "abc"),
          Arguments.of("year", "1986"),
          Arguments.of("year", 1986.5),
          Arguments.of("year", 1986.0),
          Arguments.of("name", 123),
          Arguments.of("name", 1.5),
          Arguments.of("name", true),
          Arguments.of("genres", "Action"),
          Arguments.of("director", "James Cameron"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("wrongTypes")
    void shouldReturn400NamingFieldWhenFieldHasWrongType(String field, Object value)
        throws Exception {
      // given
      Map<String, Object> body = aValidBody();
      body.put(field, value);

      // when
      ResultActions result = postJson(body);

      // then
      assertUnreadableAndServiceNotCalled(result);
      result
          .andExpect(jsonPath("$.violations.length()").value(1))
          .andExpect(jsonPath("$.violations[0].field").value(field))
          .andExpect(jsonPath("$.violations[0].message").value("has the wrong type"));
    }

    @Test
    void shouldReturn400NamingNestedPathWhenNestedFieldHasWrongType() throws Exception {
      // given
      Map<String, Object> body = aValidBody();
      body.put("actors", List.of(Map.of("firstName", "A", "lastName", 5)));

      // when
      ResultActions result = postJson(body);

      // then
      assertUnreadableAndServiceNotCalled(result);
      result
          .andExpect(jsonPath("$.violations[0].field").value("actors[0].lastName"))
          .andExpect(jsonPath("$.violations[0].message").value("has the wrong type"));
    }

    @ParameterizedTest(name = "unknown field {0}")
    @ValueSource(strings = {"synopsys", "id"})
    void shouldReturn400NamingFieldWhenBodyHasUnknownField(String unknownField) throws Exception {
      // given
      Map<String, Object> body = aValidBody();
      body.put(unknownField, "x");

      // when
      ResultActions result = postJson(body);

      // then
      assertUnreadableAndServiceNotCalled(result);
      result
          .andExpect(jsonPath("$.violations[0].field").value(unknownField))
          .andExpect(jsonPath("$.violations[0].message").value("is not a known field"));
    }

    @Test
    void shouldReturn400NamingFieldWhenNumberIsTooLargeForAnInt() throws Exception {
      // given
      Map<String, Object> body = aValidBody();
      body.put("year", 99_999_999_999L);

      // when
      ResultActions result = postJson(body);

      // then
      assertUnreadableAndServiceNotCalled(result);
      result
          .andExpect(jsonPath("$.violations.length()").value(1))
          .andExpect(jsonPath("$.violations[0].field").value("year"))
          .andExpect(jsonPath("$.violations[0].message").value("is out of range"));
    }

    @Test
    void shouldReturn413WhenBodyExceedsTheSizeLimit() throws Exception {
      // given a body just over 256 KB, made large by a synopsis that the size limit stops first
      Map<String, Object> body = aValidBody();
      body.put("synopsis", "a".repeat(256 * 1024));

      // when
      ResultActions result = postJson(body);

      // then
      expectProblem(
          result,
          HttpStatus.PAYLOAD_TOO_LARGE,
          "The request body is too large; a movie fits in 256 KB.",
          PATH);
      verifyNoInteractions(movieService);
    }
  }

  @Nested
  class Duplicate {

    @Test
    void shouldReturn409WithMovieIdWhenMovieAlreadyExists() throws Exception {
      // given
      Movie existing = MovieTestData.aMovie().withName("aliens").withYear(1986).buildMovie();
      given(movieService.createMovie(any(NewMovie.class)))
          .willThrow(new DuplicateMovieException(existing));

      // when
      ResultActions result = postJson(aValidBody());

      // then
      expectProblem(
              result,
              HttpStatus.CONFLICT,
              "A movie with the same name and year as 'aliens' (1986) already exists.",
              PATH)
          .andExpect(jsonPath("$.movieId").value(existing.id()));
    }
  }

  @Nested
  class WrongRequest {

    @Test
    void shouldReturn415ProblemWhenContentTypeIsNotJson() throws Exception {
      // given
      MockHttpServletRequestBuilder request =
          post(PATH).contentType(MediaType.TEXT_PLAIN).content(json(aValidBody()));

      // when
      ResultActions result = mockMvc.perform(request);

      // then
      expectProblem(result, HttpStatus.UNSUPPORTED_MEDIA_TYPE, PATH)
          .andExpect(jsonPath("$.detail").isNotEmpty());
      verifyNoInteractions(movieService);
    }

    @Test
    void shouldReturn406ProblemWhenClientDoesNotAcceptJson() throws Exception {
      // given
      MockHttpServletRequestBuilder request =
          post(PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.TEXT_PLAIN)
              .content(json(aValidBody()));

      // when
      ResultActions result = mockMvc.perform(request);

      // then
      expectProblem(result, HttpStatus.NOT_ACCEPTABLE, PATH)
          .andExpect(jsonPath("$.detail").isNotEmpty());
      verifyNoInteractions(movieService);
    }

    @Test
    void shouldReturn405ProblemWhenDeletingMovies() throws Exception {
      // given nothing

      // when
      ResultActions result = mockMvc.perform(delete(PATH));

      // then
      expectProblem(result, HttpStatus.METHOD_NOT_ALLOWED, PATH)
          .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void shouldReturn404ProblemWhenPathIsUnknown() throws Exception {
      // given nothing

      // when
      ResultActions result = mockMvc.perform(get("/api/v1/unknown"));

      // then
      expectProblem(
          result, HttpStatus.NOT_FOUND, "There is no endpoint at this path.", "/api/v1/unknown");
    }

    @Test
    void shouldReturn404ProblemWhenMoviePathHasTrailingSlashAndNoId() throws Exception {
      // given nothing

      // when
      ResultActions result = mockMvc.perform(get(PATH + "/"));

      // then
      expectProblem(result, HttpStatus.NOT_FOUND, "There is no endpoint at this path.", PATH + "/");
      verifyNoInteractions(movieService);
    }
  }

  @Nested
  class ServerErrors {

    @Test
    void shouldReturn500WithStorageDetailAndNoCauseWhenStoreFails() throws Exception {
      // given
      given(movieService.createMovie(any(NewMovie.class)))
          .willThrow(
              new MovieStorageException("secret internals", new IllegalStateException("grpc")));

      // when
      ResultActions result = postJson(aValidBody());

      // then
      expectProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, STORAGE_FAILURE_DETAIL, PATH)
          .andExpect(content().string(not(containsString("secret internals"))))
          .andExpect(content().string(not(containsString("grpc"))));
    }

    @Test
    void shouldReturn500WithGenericDetailAndNoCauseWhenUnexpectedErrorOccurs() throws Exception {
      // given
      given(movieService.createMovie(any(NewMovie.class)))
          .willThrow(new RuntimeException("secret internals"));

      // when
      ResultActions result = postJson(aValidBody());

      // then
      expectProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_DETAIL, PATH)
          .andExpect(content().string(not(containsString("secret internals"))));
    }
  }
}

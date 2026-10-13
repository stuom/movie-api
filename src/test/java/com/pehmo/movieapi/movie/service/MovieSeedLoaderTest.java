package com.pehmo.movieapi.movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.pehmo.movieapi.common.JacksonConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Uses the production-configured (strict) {@link ObjectMapper}, because the skip behavior depends
 * on it rejecting wrong types and unknown fields (ADR-0012).
 */
@JsonTest
@Import(JacksonConfig.class)
class MovieSeedLoaderTest {

  private static final String VALID_ALIENS =
      """
      {"name":"Aliens","year":1986,"genres":["Action"],"ageLimit":16,"rating":5,
       "director":{"firstName":"James","lastName":"Cameron"},
       "actors":[{"firstName":"Sigourney","lastName":" Weaver"}],
       "synopsis":"Ripley."}""";
  private static final String VALID_HEAT =
      """
      {"name":"Heat","year":1995,"genres":["Crime"],"ageLimit":16,"rating":4}""";
  private static final String VALID_ALIEN =
      """
      {"name":"Alien","year":1979,"genres":["Horror"],"ageLimit":18,"rating":5}""";

  @Autowired private ObjectMapper objectMapper;

  private MovieService movieService;
  private MovieCatalog catalog;

  @BeforeEach
  void createMocks() {
    movieService = mock(MovieService.class);
    catalog = mock(MovieCatalog.class);
    when(catalog.movies()).thenReturn(List.of());
  }

  private void runWith(Resource seedFile) {
    new MovieSeedLoader(movieService, catalog, objectMapper, seedFile)
        .run(new DefaultApplicationArguments());
  }

  private void runWithJson(String json) {
    runWith(new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)));
  }

  private static String arrayOf(String... entries) {
    return "[" + String.join(",", entries) + "]";
  }

  private List<NewMovie> createdMovies() {
    ArgumentCaptor<NewMovie> captor = ArgumentCaptor.forClass(NewMovie.class);
    verify(movieService, atLeast(0)).createMovie(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void shouldRejectUnknownPropertyWhenStrictMapperReadsNewMovie() {
    // when / then
    assertThatThrownBy(
            () -> objectMapper.readValue("{\"name\":\"A\",\"synopsys\":\"x\"}", NewMovie.class))
        .isInstanceOf(UnrecognizedPropertyException.class);
  }

  @Test
  void shouldNotCreateAnyMovieWhenCatalogIsNotEmpty() {
    // given
    when(catalog.movies()).thenReturn(List.of(MovieTestData.aMovie().buildMovie()));

    // when
    runWithJson(arrayOf(VALID_ALIENS, VALID_HEAT));

    // then
    verifyNoInteractions(movieService);
  }

  @Test
  void shouldCreateEachEntryWhenCatalogIsEmpty() {
    // when
    runWithJson(arrayOf(VALID_ALIENS, VALID_HEAT, VALID_ALIEN));

    // then
    assertThat(createdMovies())
        .extracting(NewMovie::name)
        .containsExactly("Aliens", "Heat", "Alien");
  }

  @Test
  void shouldPassValuesUntrimmedWhenEntryHasStraySpaces() {
    // when
    runWithJson(arrayOf(VALID_ALIENS));

    // then
    NewMovie created = createdMovies().get(0);
    assertThat(created.actors()).containsExactly(new NewMovie.PersonInput("Sigourney", " Weaver"));
    assertThat(created.year()).isEqualTo(1986);
    assertThat(created.director()).isEqualTo(new NewMovie.PersonInput("James", "Cameron"));
  }

  @Test
  void shouldSkipEntryWithWrongTypeAndCreateTheFollowingEntries() {
    // given
    String yearAsText = "{\"name\":\"Bad\",\"year\":\"1986\",\"genres\":[\"Action\"]}";

    // when
    runWithJson(arrayOf(VALID_ALIENS, yearAsText, VALID_HEAT));

    // then
    assertThat(createdMovies()).extracting(NewMovie::name).containsExactly("Aliens", "Heat");
  }

  @Test
  void shouldSkipEntryWithUnknownFieldAndCreateTheFollowingEntries() {
    // given
    String typo = "{\"name\":\"Typo\",\"year\":1986,\"synopsys\":\"misspelled\"}";

    // when
    runWithJson(arrayOf(typo, VALID_HEAT));

    // then
    assertThat(createdMovies()).extracting(NewMovie::name).containsExactly("Heat");
  }

  @Test
  void shouldSkipEntryRejectedAsInvalidAndCreateTheFollowingEntries() {
    // given
    when(movieService.createMovie(any(NewMovie.class)))
        .thenThrow(new InvalidMovieException(List.of(new FieldViolation("name", "required"))))
        .thenReturn(null);

    // when
    runWithJson(arrayOf(VALID_ALIENS, VALID_HEAT));

    // then
    assertThat(createdMovies()).extracting(NewMovie::name).containsExactly("Aliens", "Heat");
  }

  @Test
  void shouldSkipEntryRejectedAsDuplicateAndCreateTheFollowingEntries() {
    // given
    Movie existing = MovieTestData.aMovie().buildMovie();
    when(movieService.createMovie(any(NewMovie.class)))
        .thenThrow(new DuplicateMovieException(existing))
        .thenReturn(null);

    // when
    runWithJson(arrayOf(VALID_ALIENS, VALID_HEAT));

    // then
    assertThat(createdMovies()).extracting(NewMovie::name).containsExactly("Aliens", "Heat");
  }

  @Test
  void shouldStopSeedingWithoutThrowingWhenStorageFailsPartWay() {
    // given
    when(movieService.createMovie(any(NewMovie.class)))
        .thenReturn(null)
        .thenThrow(new MovieStorageException("store down", new IllegalStateException("boom")));

    // when
    runWithJson(arrayOf(VALID_ALIENS, VALID_HEAT, VALID_ALIEN));

    // then
    assertThat(createdMovies()).extracting(NewMovie::name).containsExactly("Aliens", "Heat");
  }

  @Test
  void shouldThrowIllegalStateExceptionWhenFileIsNotJsonArray() {
    // when / then
    assertThatThrownBy(() -> runWithJson("{}")).isInstanceOf(IllegalStateException.class);
    verify(movieService, never()).createMovie(any(NewMovie.class));
  }

  @Test
  void shouldThrowIllegalStateExceptionWhenFileIsEmpty() {
    // when / then
    assertThatThrownBy(() -> runWithJson("")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldThrowIllegalStateExceptionWhenFileIsNotJson() {
    // when / then
    assertThatThrownBy(() -> runWithJson("this is not json"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldCreateTwentyMoviesFromTheBundledSeedFile() {
    // when
    runWith(new ClassPathResource("seed/movies.json"));

    // then
    verify(movieService, times(20)).createMovie(any(NewMovie.class));
  }
}

package com.pehmo.movieapi.movie.service;

import static com.pehmo.movieapi.movie.service.MovieTestData.aNewMovie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pehmo.movieapi.movie.persistence.MovieRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);

  @Mock private MovieRepository repository;
  @Mock private MovieCatalog catalog;

  private MovieService service;

  @BeforeEach
  void createService() {
    service = new MovieService(repository, new MovieRules(FIXED_CLOCK), catalog);
  }

  @Test
  void shouldStoreAndReturnTrimmedMovieWithDerivedIdWhenInputIsValid() {
    // given
    NewMovie newMovie =
        aNewMovie()
            .withName("  Aliens ")
            .withYear(1986)
            .withGenres(List.of(" Action "))
            .withDirector(MovieTestData.aPerson(" James", "Cameron "))
            .withActors(List.of())
            .withSynopsis(null)
            .build();

    // when
    Movie created = service.createMovie(newMovie);

    // then
    ArgumentCaptor<Movie> stored = ArgumentCaptor.forClass(Movie.class);
    verify(repository).create(stored.capture());
    assertThat(created).isEqualTo(stored.getValue());
    assertThat(created.id()).isEqualTo(MovieId.derivedFrom("Aliens", 1986));
    assertThat(created.name()).isEqualTo("Aliens");
    assertThat(created.genres()).containsExactly("Action");
    assertThat(created.director()).isEqualTo(new Person("James", "Cameron"));
    assertThat(created.actors()).isEmpty();
    assertThat(created.synopsis()).isNull();
  }

  @Test
  void shouldAddCreatedMovieToCatalogWhenCreateSucceeds() {
    // given
    NewMovie newMovie = aNewMovie().build();

    // when
    Movie created = service.createMovie(newMovie);

    // then
    verify(catalog).include(created);
  }

  @Test
  void shouldThrowInvalidMovieExceptionWithoutCallingRepositoryWhenMovieBreaksRules() {
    // given
    NewMovie newMovie = aNewMovie().withName(" ").withRating(9).build();

    // when / then
    assertThatThrownBy(() -> service.createMovie(newMovie))
        .isInstanceOfSatisfying(
            InvalidMovieException.class,
            thrown ->
                assertThat(thrown.violations())
                    .extracting(FieldViolation::field)
                    .containsExactly("name", "rating"));
    verifyNoInteractions(repository);
  }

  @Test
  void shouldPropagateDuplicateMovieExceptionWhenRepositoryReportsDuplicate() {
    // given
    DuplicateMovieException duplicate =
        new DuplicateMovieException(MovieTestData.aMovie().buildMovie());
    willThrow(duplicate).given(repository).create(any(Movie.class));

    // when / then
    assertThatThrownBy(() -> service.createMovie(aNewMovie().build())).isSameAs(duplicate);
  }

  @Test
  void shouldPropagateMovieStorageExceptionWhenRepositoryFails() {
    // given
    MovieStorageException failure =
        new MovieStorageException("store down", new IllegalStateException("boom"));
    willThrow(failure).given(repository).create(any(Movie.class));

    // when / then
    assertThatThrownBy(() -> service.createMovie(aNewMovie().build())).isSameAs(failure);
  }

  @Test
  void shouldNotTouchCatalogWhenMovieBreaksRules() {
    // given
    NewMovie newMovie = aNewMovie().withName(" ").build();

    // when
    assertThatThrownBy(() -> service.createMovie(newMovie))
        .isInstanceOf(InvalidMovieException.class);

    // then
    verifyNoInteractions(catalog);
  }

  @Test
  void shouldNotTouchCatalogWhenRepositoryReportsDuplicate() {
    // given
    willThrow(new DuplicateMovieException(MovieTestData.aMovie().buildMovie()))
        .given(repository)
        .create(any(Movie.class));

    // when
    assertThatThrownBy(() -> service.createMovie(aNewMovie().build()))
        .isInstanceOf(DuplicateMovieException.class);

    // then
    verifyNoInteractions(catalog);
  }

  @Test
  void shouldReturnMovieWhenCatalogHasId() {
    // given
    Movie movie = MovieTestData.aMovie().buildMovie();
    when(catalog.findById(movie.id())).thenReturn(Optional.of(movie));

    // when
    Movie found = service.findMovie(movie.id());

    // then
    assertThat(found).isEqualTo(movie);
  }

  @Test
  void shouldThrowMovieNotFoundExceptionWithIdWhenCatalogDoesNotHaveId() {
    // given
    when(catalog.findById("missing")).thenReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> service.findMovie("missing"))
        .isInstanceOfSatisfying(
            MovieNotFoundException.class, thrown -> assertThat(thrown.id()).isEqualTo("missing"));
  }

  @Test
  void shouldNotTouchRepositoryWhenFindingMovie() {
    // given
    when(catalog.findById("missing")).thenReturn(Optional.empty());

    // when
    assertThatThrownBy(() -> service.findMovie("missing"))
        .isInstanceOf(MovieNotFoundException.class);

    // then
    verifyNoInteractions(repository);
  }

  @Test
  void shouldReturnSortedPageOfCatalogSnapshotWhenSearchingWithoutCriteria() {
    // given
    Movie brazil = MovieTestData.aMovie().withName("Brazil").buildMovie();
    Movie alien = MovieTestData.aMovie().withName("Alien").buildMovie();
    Movie heat = MovieTestData.aMovie().withName("Heat").buildMovie();
    when(catalog.movies()).thenReturn(List.of(brazil, alien, heat));
    MovieQuery query = new MovieQuery(new MovieSearchCriteria.All(), new MoviePageRequest(1, 2));

    // when
    MoviePage page = service.searchMovies(query);

    // then
    assertThat(page.items()).containsExactly(heat);
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.size()).isEqualTo(2);
    assertThat(page.totalItems()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(2);
  }

  @Test
  void shouldReturnOnlyMatchingMoviesOfCatalogSnapshotWhenCriteriaAreGiven() {
    // given
    Movie brazil =
        MovieTestData.aMovie().withName("Brazil").withGenres(List.of("Sci-fi")).buildMovie();
    Movie heat = MovieTestData.aMovie().withName("Heat").withGenres(List.of("Crime")).buildMovie();
    when(catalog.movies()).thenReturn(List.of(brazil, heat));
    MovieQuery query =
        new MovieQuery(new MovieSearchCriteria.Genre("crime"), new MoviePageRequest(0, 20));

    // when
    MoviePage page = service.searchMovies(query);

    // then
    assertThat(page.items()).containsExactly(heat);
    assertThat(page.totalItems()).isEqualTo(1);
  }

  @Test
  void shouldReturnEmptyPageWhenCatalogIsEmpty() {
    // given
    when(catalog.movies()).thenReturn(List.of());

    // when
    MoviePage page =
        service.searchMovies(
            new MovieQuery(new MovieSearchCriteria.All(), new MoviePageRequest(0, 20)));

    // then
    assertThat(page.items()).isEmpty();
    assertThat(page.totalItems()).isZero();
  }

  @Test
  void shouldNotTouchCatalogWhenRepositoryFails() {
    // given
    willThrow(new MovieStorageException("store down", new IllegalStateException("boom")))
        .given(repository)
        .create(any(Movie.class));

    // when
    assertThatThrownBy(() -> service.createMovie(aNewMovie().build()))
        .isInstanceOf(MovieStorageException.class);

    // then
    verifyNoInteractions(catalog);
  }
}

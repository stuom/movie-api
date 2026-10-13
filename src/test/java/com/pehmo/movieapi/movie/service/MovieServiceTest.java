package com.pehmo.movieapi.movie.service;

import static com.pehmo.movieapi.movie.service.MovieTestData.aNewMovie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pehmo.movieapi.movie.persistence.MovieRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

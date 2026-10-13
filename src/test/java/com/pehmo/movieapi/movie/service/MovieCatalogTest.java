package com.pehmo.movieapi.movie.service;

import static com.pehmo.movieapi.movie.service.MovieTestData.aMovie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pehmo.movieapi.movie.persistence.MovieRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MovieCatalogTest {

  private static final Duration INTERVAL = Duration.ofMinutes(60);

  private final Movie aliens = aMovie().withName("Aliens").withYear(1986).buildMovie();
  private final Movie alien = aMovie().withName("Alien").withYear(1979).buildMovie();
  private final Movie heat = aMovie().withName("Heat").withYear(1995).buildMovie();

  private MovieRepository repository;
  private MutableClock clock;

  @BeforeEach
  void createCollaborators() {
    repository = mock(MovieRepository.class);
    clock = new MutableClock(Instant.parse("2026-06-15T10:00:00Z"));
  }

  private MovieCatalog catalog() {
    return new MovieCatalog(repository, clock, INTERVAL);
  }

  private static MovieStorageException storageFailure() {
    return new MovieStorageException("store down", new IllegalStateException("boom"));
  }

  @Nested
  class Construction {

    @Test
    void shouldLoadAllMoviesFromRepositoryWhenCreated() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens, alien));

      // when
      MovieCatalog catalog = catalog();

      // then
      assertThat(catalog.movies()).containsExactlyInAnyOrder(aliens, alien);
      verify(repository, times(1)).findAll();
    }

    @Test
    void shouldBeEmptyWhenRepositoryHasNoMovies() {
      // given
      when(repository.findAll()).thenReturn(List.of());

      // when
      MovieCatalog catalog = catalog();

      // then
      assertThat(catalog.movies()).isEmpty();
    }

    @Test
    void shouldRejectIntervalWhenRefreshIntervalIsZero() {
      // when / then
      assertThatThrownBy(() -> new MovieCatalog(repository, clock, Duration.ZERO))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectIntervalWhenRefreshIntervalIsNegative() {
      // when / then
      assertThatThrownBy(() -> new MovieCatalog(repository, clock, Duration.ofMinutes(-1)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPropagateMovieStorageExceptionWhenInitialLoadFails() {
      // given
      MovieStorageException failure = storageFailure();
      when(repository.findAll()).thenThrow(failure);

      // when / then
      assertThatThrownBy(() -> catalog()).isSameAs(failure);
    }

    @Test
    void shouldRejectChangesWhenCallerModifiesReturnedList() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens));
      MovieCatalog catalog = catalog();

      // when / then
      assertThatThrownBy(() -> catalog.movies().add(heat))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  class Refresh {

    @Test
    void shouldNotReloadWhenIntervalHasNotElapsed() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens));
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL.minusNanos(1));

      // when
      catalog.movies();

      // then
      verify(repository, times(1)).findAll();
    }

    @Test
    void shouldReloadWhenIntervalHasElapsedExactly() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens)).thenReturn(List.of(heat));
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL);

      // when
      List<Movie> movies = catalog.movies();

      // then
      assertThat(movies).containsExactly(heat);
      verify(repository, times(2)).findAll();
    }

    @Test
    void shouldWaitFullIntervalFromLastLoadBeforeReloadingAgain() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens));
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL);
      catalog.movies();
      clock.advance(INTERVAL.minusNanos(1));

      // when
      catalog.movies();

      // then
      verify(repository, times(2)).findAll();
    }

    @Test
    void shouldKeepMovieAddedWhileRefreshIsLoadingEvenWhenLoadedListLacksIt() {
      // given
      AtomicReference<MovieCatalog> catalogRef = new AtomicReference<>();
      when(repository.findAll())
          .thenReturn(List.of(aliens))
          .thenAnswer(
              invocation -> {
                catalogRef.get().include(heat);
                return List.of(aliens);
              });
      MovieCatalog catalog = catalog();
      catalogRef.set(catalog);
      clock.advance(INTERVAL);

      // when
      List<Movie> movies = catalog.movies();

      // then
      assertThat(movies).containsExactlyInAnyOrder(aliens, heat);
    }

    @Test
    void shouldNotStartSecondLoadWhenCatalogIsReadDuringLoad() {
      // given
      AtomicReference<MovieCatalog> catalogRef = new AtomicReference<>();
      AtomicReference<List<Movie>> seenDuringLoad = new AtomicReference<>();
      when(repository.findAll())
          .thenReturn(List.of(aliens))
          .thenAnswer(
              invocation -> {
                seenDuringLoad.set(catalogRef.get().movies());
                return List.of(heat);
              });
      MovieCatalog catalog = catalog();
      catalogRef.set(catalog);
      clock.advance(INTERVAL);

      // when
      catalog.movies();

      // then
      assertThat(seenDuringLoad.get()).containsExactly(aliens);
      verify(repository, times(2)).findAll();
    }
  }

  @Test
  void shouldTimeNextReloadFromStartOfLoadWhenLoadTakesTime() {
    // given
    when(repository.findAll())
        .thenReturn(List.of(aliens))
        .thenAnswer(
            invocation -> {
              clock.advance(Duration.ofSeconds(20));
              return List.of(aliens);
            });
    MovieCatalog catalog = catalog();
    clock.advance(INTERVAL);
    catalog.movies(); // reload that takes 20 seconds
    clock.advance(INTERVAL.minusSeconds(20));

    // when
    catalog.movies();

    // then
    verify(repository, times(3)).findAll();
  }

  @Nested
  class FailedRefresh {

    @Test
    void shouldKeepServingOldSnapshotWhenRefreshFailsWithStorageException() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens)).thenThrow(storageFailure());
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL);

      // when
      List<Movie> movies = catalog.movies();

      // then
      assertThat(movies).containsExactly(aliens);
    }

    @Test
    void shouldNotRetryBeforeFullIntervalHasPassedSinceFailedAttempt() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens)).thenThrow(storageFailure());
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL);
      catalog.movies();
      clock.advance(INTERVAL.minusNanos(1));

      // when
      catalog.movies();

      // then
      verify(repository, times(2)).findAll();
    }

    @Test
    void shouldReplaceSnapshotWhenRetryAfterFailureSucceeds() {
      // given
      when(repository.findAll())
          .thenReturn(List.of(aliens))
          .thenThrow(storageFailure())
          .thenReturn(List.of(heat));
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL);
      catalog.movies();
      clock.advance(INTERVAL);

      // when
      List<Movie> movies = catalog.movies();

      // then
      assertThat(movies).containsExactly(heat);
    }

    @Test
    void shouldPropagateUnexpectedExceptionAndKeepOldSnapshotWhenRefreshCrashes() {
      // given
      IllegalStateException crash = new IllegalStateException("bug");
      when(repository.findAll()).thenReturn(List.of(aliens)).thenThrow(crash);
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL);

      // when / then
      assertThatThrownBy(catalog::movies).isSameAs(crash);
      assertThat(catalog.movies()).containsExactly(aliens);
    }

    @Test
    void shouldLoadAgainAfterNextIntervalWhenPreviousRefreshCrashed() {
      // given
      when(repository.findAll())
          .thenReturn(List.of(aliens))
          .thenThrow(new IllegalStateException("bug"))
          .thenReturn(List.of(heat));
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL);
      assertThatThrownBy(catalog::movies).isInstanceOf(IllegalStateException.class);
      clock.advance(INTERVAL);

      // when
      List<Movie> movies = catalog.movies();

      // then
      assertThat(movies).containsExactly(heat);
      verify(repository, times(3)).findAll();
    }
  }

  @Nested
  class FindingById {

    @Test
    void shouldReturnMovieWhenIdIsInCatalog() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens, heat));
      MovieCatalog catalog = catalog();

      // when
      Optional<Movie> found = catalog.findById(heat.id());

      // then
      assertThat(found).contains(heat);
    }

    @Test
    void shouldReturnEmptyWhenIdIsUnknown() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens));
      MovieCatalog catalog = catalog();

      // when
      Optional<Movie> found = catalog.findById("unknown-id");

      // then
      assertThat(found).isEmpty();
    }

    @Test
    void shouldFindMovieWhenItWasIncluded() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens));
      MovieCatalog catalog = catalog();
      catalog.include(heat);

      // when
      Optional<Movie> found = catalog.findById(heat.id());

      // then
      assertThat(found).contains(heat);
    }

    @Test
    void shouldReloadWhenIntervalHasElapsed() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens)).thenReturn(List.of(heat));
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL);

      // when
      Optional<Movie> found = catalog.findById(heat.id());

      // then
      assertThat(found).contains(heat);
      assertThat(catalog.findById(aliens.id())).isEmpty();
      verify(repository, times(2)).findAll();
    }

    @Test
    void shouldNotReloadWhenIntervalHasNotElapsed() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens));
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL.minusNanos(1));

      // when
      catalog.findById(aliens.id());

      // then
      verify(repository, times(1)).findAll();
    }

    @Test
    void shouldServePreviousCopyWhenReloadFails() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens)).thenThrow(storageFailure());
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL);

      // when
      Optional<Movie> found = catalog.findById(aliens.id());

      // then
      assertThat(found).contains(aliens);
    }
  }

  @Nested
  class Including {

    @Test
    void shouldShowMovieImmediatelyWhenIncluded() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens));
      MovieCatalog catalog = catalog();

      // when
      catalog.include(heat);

      // then
      assertThat(catalog.movies()).containsExactlyInAnyOrder(aliens, heat);
    }

    @Test
    void shouldContainOnlyIncludedMovieAfterAddingToEmptyCatalog() {
      // given
      when(repository.findAll()).thenReturn(List.of());
      MovieCatalog catalog = catalog();

      // when
      catalog.include(heat);

      // then
      assertThat(catalog.movies()).containsExactly(heat);
    }

    @Test
    void shouldReplaceMovieWithSameIdInsteadOfDuplicatingIt() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens));
      MovieCatalog catalog = catalog();

      // when
      catalog.include(aliens);

      // then
      assertThat(catalog.movies()).containsExactly(aliens);
    }

    @Test
    void shouldNotDuplicateMovieWhenRefreshAlreadyLoadedItBeforeAdd() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens)).thenReturn(List.of(aliens, heat));
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL);
      catalog.movies();

      // when
      catalog.include(heat);

      // then
      assertThat(catalog.movies()).containsExactlyInAnyOrder(aliens, heat);
    }

    @Test
    void shouldNotDelayNextReloadWhenMovieIsIncluded() {
      // given
      when(repository.findAll()).thenReturn(List.of(aliens));
      MovieCatalog catalog = catalog();
      clock.advance(INTERVAL.minusNanos(1));
      catalog.include(heat);
      catalog.movies();
      verify(repository, times(1)).findAll(); // guard: still within the first interval

      // when
      clock.advance(Duration.ofNanos(1));
      catalog.movies();

      // then
      verify(repository, times(2)).findAll();
    }
  }
}

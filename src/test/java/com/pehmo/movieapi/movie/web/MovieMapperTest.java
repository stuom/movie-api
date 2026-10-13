package com.pehmo.movieapi.movie.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.pehmo.movieapi.movie.service.Movie;
import com.pehmo.movieapi.movie.service.MoviePage;
import com.pehmo.movieapi.movie.service.MovieTestData;
import com.pehmo.movieapi.movie.service.NewMovie;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MovieMapperTest {

  @Nested
  class ToNewMovie {

    @Test
    void shouldCopyEveryFieldWhenRequestIsComplete() {
      // given
      NewMovieRequest request =
          new NewMovieRequest(
              "Aliens",
              1986,
              List.of("Action", "Sci-fi"),
              16,
              5,
              new PersonDto("James", "Cameron"),
              List.of(new PersonDto("Sigourney", "Weaver"), new PersonDto("Michael", "Biehn")),
              "Ellen Ripley");

      // when
      NewMovie newMovie = MovieMapper.toNewMovie(request);

      // then
      assertThat(newMovie)
          .isEqualTo(
              new NewMovie(
                  "Aliens",
                  1986,
                  List.of("Action", "Sci-fi"),
                  16,
                  5,
                  new NewMovie.PersonInput("James", "Cameron"),
                  List.of(
                      new NewMovie.PersonInput("Sigourney", "Weaver"),
                      new NewMovie.PersonInput("Michael", "Biehn")),
                  "Ellen Ripley"));
    }

    @Test
    void shouldKeepNullsWhenRequestFieldsAreMissing() {
      // given
      NewMovieRequest request = new NewMovieRequest(null, null, null, null, null, null, null, null);

      // when
      NewMovie newMovie = MovieMapper.toNewMovie(request);

      // then
      assertThat(newMovie).isEqualTo(new NewMovie(null, null, null, null, null, null, null, null));
    }

    @Test
    void shouldKeepNullEntriesWhenListsContainNulls() {
      // given
      NewMovieRequest request =
          new NewMovieRequest(
              "Aliens",
              1986,
              Arrays.asList("Action", null),
              16,
              5,
              null,
              Arrays.asList(new PersonDto("A", "B"), null),
              null);

      // when
      NewMovie newMovie = MovieMapper.toNewMovie(request);

      // then
      assertThat(newMovie.genres()).containsExactly("Action", null);
      assertThat(newMovie.actors()).containsExactly(new NewMovie.PersonInput("A", "B"), null);
    }

    @Test
    void shouldKeepPersonNamesAsEnteredWhenOneNameIsMissing() {
      // given
      NewMovieRequest request =
          new NewMovieRequest(
              "Aliens", 1986, List.of("Action"), 16, 5, new PersonDto("James", null), null, null);

      // when
      NewMovie newMovie = MovieMapper.toNewMovie(request);

      // then
      assertThat(newMovie.director()).isEqualTo(new NewMovie.PersonInput("James", null));
    }
  }

  @Nested
  class ToPageResponse {

    @Test
    void shouldMapItemsInOrderAndCopyCountsWhenPageHasMovies() {
      // given
      Movie alien = MovieTestData.aMovie().withName("Alien").buildMovie();
      Movie heat = MovieTestData.aMovie().withName("Heat").buildMovie();
      MoviePage page = new MoviePage(List.of(alien, heat), 1, 2, 5, 3);

      // when
      MoviePageResponse response = MovieMapper.toPageResponse(page);

      // then
      assertThat(response.items())
          .containsExactly(MovieMapper.toResponse(alien), MovieMapper.toResponse(heat));
      assertThat(response.page()).isEqualTo(1);
      assertThat(response.size()).isEqualTo(2);
      assertThat(response.totalItems()).isEqualTo(5);
      assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void shouldMapNoItemsWhenPageIsEmpty() {
      // given
      MoviePage page = new MoviePage(List.of(), 0, 20, 0, 0);

      // when
      MoviePageResponse response = MovieMapper.toPageResponse(page);

      // then
      assertThat(response.items()).isEmpty();
      assertThat(response.totalItems()).isZero();
      assertThat(response.totalPages()).isZero();
    }
  }

  @Nested
  class ToResponse {

    @Test
    void shouldCopyEveryFieldWhenMovieIsComplete() {
      // given
      Movie movie = MovieTestData.aMovie().buildMovie();

      // when
      MovieResponse response = MovieMapper.toResponse(movie);

      // then
      assertThat(response)
          .isEqualTo(
              new MovieResponse(
                  movie.id(),
                  "Aliens",
                  1986,
                  List.of("Action", "Sci-fi"),
                  16,
                  5,
                  new PersonDto("James", "Cameron"),
                  List.of(new PersonDto("Sigourney", "Weaver")),
                  "Ellen Ripley is rescued by a deep salvage team."));
    }

    @Test
    void shouldReturnNullDirectorAndSynopsisAndNoActorsWhenMovieHasNone() {
      // given
      Movie movie =
          MovieTestData.aMovie()
              .withDirector(null)
              .withActors(null)
              .withSynopsis(null)
              .buildMovie();

      // when
      MovieResponse response = MovieMapper.toResponse(movie);

      // then
      assertThat(response.director()).isNull();
      assertThat(response.synopsis()).isNull();
      assertThat(response.actors()).isEmpty();
    }

    @Test
    void shouldKeepActorOrderWhenMovieHasSeveralActors() {
      // given
      Movie movie =
          MovieTestData.aMovie()
              .withActors(
                  List.of(
                      MovieTestData.aPerson("Zed", "Last"), MovieTestData.aPerson("Amy", "First")))
              .buildMovie();

      // when
      MovieResponse response = MovieMapper.toResponse(movie);

      // then
      assertThat(response.actors())
          .containsExactly(new PersonDto("Zed", "Last"), new PersonDto("Amy", "First"));
    }
  }
}

package com.pehmo.movieapi.movie.service;

import static com.pehmo.movieapi.movie.service.MovieTestData.aMovie;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MovieSearchTest {

  private static Movie movieNamed(String name) {
    return aMovie().withName(name).buildMovie();
  }

  /** Movies "Movie 001", "Movie 002", ... whose natural order is the creation order. */
  private static List<Movie> numberedMovies(int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(number -> movieNamed("Movie %03d".formatted(number)))
        .toList();
  }

  private static List<String> namesOf(MoviePage page) {
    return page.items().stream().map(Movie::name).toList();
  }

  @Nested
  class Paging {

    @Test
    void shouldReturnEmptyPageWithZeroTotalsWhenCatalogIsEmpty() {
      // given
      List<Movie> movies = List.of();

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(0, 20));

      // then
      assertThat(page.items()).isEmpty();
      assertThat(page.totalItems()).isZero();
      assertThat(page.totalPages()).isZero();
    }

    @Test
    void shouldReturnFullFirstPageWhenCatalogIsLargerThanPage() {
      // given
      List<Movie> movies = numberedMovies(5);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(0, 2));

      // then
      assertThat(namesOf(page)).containsExactly("Movie 001", "Movie 002");
      assertThat(page.totalItems()).isEqualTo(5);
      assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void shouldReturnShortLastPageWhenItemsDoNotFillIt() {
      // given
      List<Movie> movies = numberedMovies(5);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(2, 2));

      // then
      assertThat(namesOf(page)).containsExactly("Movie 005");
      assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void shouldReturnFullLastPageWhenItemsFillItExactly() {
      // given
      List<Movie> movies = numberedMovies(4);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(1, 2));

      // then
      assertThat(namesOf(page)).containsExactly("Movie 003", "Movie 004");
      assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyItemsWithTrueTotalsWhenPageIsPastTheEnd() {
      // given
      List<Movie> movies = numberedMovies(5);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(3, 2));

      // then
      assertThat(page.items()).isEmpty();
      assertThat(page.totalItems()).isEqualTo(5);
      assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyItemsWithoutOverflowWhenPageIsIntegerMaxValue() {
      // given
      List<Movie> movies = numberedMovies(5);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(Integer.MAX_VALUE, 100));

      // then
      assertThat(page.items()).isEmpty();
      assertThat(page.totalItems()).isEqualTo(5);
    }

    @Test
    void shouldReturnHundredItemsAndTwoPagesWhenSizeIsMaximumAndCatalogHasHundredFifty() {
      // given
      List<Movie> movies = numberedMovies(150);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(0, 100));

      // then
      assertThat(page.items()).hasSize(100);
      assertThat(page.totalItems()).isEqualTo(150);
      assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldEchoRequestedPageAndSizeWhenPageIsReturned() {
      // given
      List<Movie> movies = numberedMovies(5);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(1, 3));

      // then
      assertThat(page.page()).isEqualTo(1);
      assertThat(page.size()).isEqualTo(3);
    }

    @Test
    void shouldEchoRequestedPageAndSizeWhenPageIsPastTheEnd() {
      // given
      List<Movie> movies = numberedMovies(1);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(7, 4));

      // then
      assertThat(page.page()).isEqualTo(7);
      assertThat(page.size()).isEqualTo(4);
    }
  }

  @Nested
  class Ordering {

    @Test
    void shouldOrderByNameIgnoringCaseWhenNamesDifferInCase() {
      // given
      List<Movie> movies = List.of(movieNamed("brazil"), movieNamed("Aliens"), movieNamed("alien"));

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(0, 20));

      // then
      assertThat(namesOf(page)).containsExactly("alien", "Aliens", "brazil");
    }

    @Test
    void shouldOrderByIdWhenNamesAreEqualAfterNormalization() {
      // given
      Movie first = aMovie().withName("Heat").withId("a-id").buildMovie();
      Movie second = aMovie().withName("heat ").withId("b-id").buildMovie();
      Movie third = aMovie().withName("HEAT").withId("c-id").buildMovie();
      List<Movie> movies = List.of(third, first, second);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(0, 20));

      // then
      assertThat(page.items()).containsExactly(first, second, third);
    }

    @Test
    void shouldTreatCollapsedWhitespaceAsEqualNamesWhenOrderingById() {
      // given
      Movie spaced = aMovie().withName("The   Matrix").withId("a-id").buildMovie();
      Movie single = aMovie().withName("The Matrix").withId("b-id").buildMovie();
      List<Movie> movies = List.of(single, spaced);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(0, 20));

      // then
      assertThat(page.items()).containsExactly(spaced, single);
    }

    @Test
    void shouldOrderBeforePagingWhenInputIsUnsorted() {
      // given
      List<Movie> movies = new ArrayList<>(numberedMovies(5));
      Collections.reverse(movies);

      // when
      MoviePage page = MovieSearch.pageOf(movies, new MoviePageRequest(1, 2));

      // then
      assertThat(namesOf(page)).containsExactly("Movie 003", "Movie 004");
    }
  }

  @Test
  void shouldNotModifyInputListWhenSorting() {
    // given
    List<Movie> movies = new ArrayList<>(List.of(movieNamed("b"), movieNamed("a")));
    List<Movie> unmodifiableMovies = Collections.unmodifiableList(movies);
    List<Movie> before = List.copyOf(movies);

    // when
    MovieSearch.pageOf(unmodifiableMovies, new MoviePageRequest(0, 20));

    // then
    assertThat(movies).containsExactlyElementsOf(before);
  }
}

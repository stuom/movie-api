package com.pehmo.movieapi.movie.service;

import static com.pehmo.movieapi.movie.service.MovieTestData.aMovie;
import static com.pehmo.movieapi.movie.service.MovieTestData.aPerson;
import static org.assertj.core.api.Assertions.assertThat;

import com.pehmo.movieapi.movie.service.MovieSearchCriteria.ActorNamed;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.DirectorNamed;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.Genre;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.NameContains;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.RatingRange;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.SynopsisContains;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.ViewerAge;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.YearRange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MovieSearchTest {

  // A small catalog modeled on the sample data (src/main/resources/seed/movies.json).
  private static final Movie AD_ASTRA =
      aMovie()
          .withName("Ad Astra")
          .withYear(2019)
          .withGenres(List.of("Adventure", "Drama", "Mystery"))
          .withAgeLimit(12)
          .withRating(3)
          .withDirector(aPerson("James", "Grey"))
          .withActors(List.of(aPerson("Brad", "Pitt"), aPerson("Tommy", "Lee Jones")))
          .withSynopsis(
              "Astronaut Roy McBride undertakes a mission across an unforgiving solar system to"
                  + " uncover the truth about his missing father and his doomed expedition.")
          .buildMovie();
  private static final Movie ALIENS =
      aMovie()
          .withName("Aliens")
          .withYear(1986)
          .withGenres(List.of("Action", "Adventure", "Sci-fi"))
          .withAgeLimit(16)
          .withRating(5)
          .withDirector(aPerson("James", "Cameron"))
          .withActors(List.of(aPerson("Sigourney", "Weaver")))
          .withSynopsis("Ellen Ripley is rescued by a deep salvage team after being in hypersleep.")
          .buildMovie();
  private static final Movie BLADE_RUNNER =
      aMovie()
          .withName("Blade Runner 2049")
          .withYear(2017)
          .withGenres(List.of("Action", "Drama", "Mystery"))
          .withAgeLimit(15)
          .withRating(5)
          .withDirector(aPerson("Denis", "Villeneuve"))
          .withActors(
              List.of(
                  aPerson("Ryan", "Gosling"),
                  aPerson("Harrison", "Ford"),
                  aPerson("Robin", "Wright")))
          .withSynopsis(
              "A young blade runner's discovery leads him to Rick Deckard, who's missing.")
          .buildMovie();
  private static final Movie DRIVE =
      aMovie()
          .withName("Drive")
          .withYear(2011)
          .withGenres(List.of("Crime", "Drama"))
          .withAgeLimit(18)
          .withRating(4)
          .withDirector(aPerson("Nicholas", "Winding Refn"))
          .withActors(List.of(aPerson("Ryan", "Gosling"), aPerson("Carey", "Mulligan")))
          .withSynopsis("A mysterious Hollywood stuntman moonlights as a getaway driver.")
          .buildMovie();
  private static final Movie RAIDERS =
      aMovie()
          .withName("Raiders of the Lost Ark")
          .withYear(1981)
          .withGenres(List.of("Action", "Adventure"))
          .withAgeLimit(0)
          .withRating(4)
          .withDirector(aPerson("Steven", "Spielberg"))
          .withActors(List.of(aPerson("Harrison", "Ford"), aPerson("Karen", "Allen")))
          .withSynopsis("Indiana Jones is hired to find the Ark of the Covenant.")
          .buildMovie();
  private static final Movie SHAPE_OF_WATER =
      aMovie()
          .withName("The Shape of Water")
          .withYear(2017)
          .withGenres(List.of("Adventure", "Drama", "Fantasy"))
          .withAgeLimit(16)
          .withRating(4)
          .withDirector(aPerson("Guillermo", "del Toro"))
          .withActors(List.of(aPerson("Sally", "Hawkins"), aPerson("Michael", "Shannon")))
          .withSynopsis("A lonely janitor forms a relationship with an amphibious creature.")
          .buildMovie();
  private static final Movie SPIRITED_AWAY =
      aMovie()
          .withName("Spirited Away")
          .withYear(2001)
          .withGenres(List.of("Animation", "Adventure", "Family"))
          .withAgeLimit(0)
          .withRating(5)
          .withDirector(aPerson("Hayao", "Miyazaki"))
          .withActors(List.of(aPerson("Rumi", "Hiiragi"), aPerson("Miyu", "Irino")))
          .withSynopsis("A sullen girl wanders into a world ruled by gods and witches.")
          .buildMovie();

  /** A movie with only the required fields: no director, no actors and no synopsis. */
  private static final Movie BARE =
      aMovie()
          .withName("Bare")
          .withYear(1975)
          .withGenres(List.of("Documentary"))
          .withAgeLimit(10)
          .withRating(1)
          .withDirector(null)
          .withActors(List.of())
          .withSynopsis(null)
          .buildMovie();

  private static final List<Movie> CATALOG =
      List.of(AD_ASTRA, ALIENS, BLADE_RUNNER, DRIVE, RAIDERS, SHAPE_OF_WATER, SPIRITED_AWAY, BARE);
  private static final MoviePageRequest EVERYTHING_ON_ONE_PAGE = new MoviePageRequest(0, 100);

  private static List<String> namesMatching(MovieSearchCriteria criteria) {
    return namesOf(MovieSearch.search(CATALOG, criteria, EVERYTHING_ON_ONE_PAGE));
  }

  private static List<String> namesMatching(Movie movie, MovieSearchCriteria criteria) {
    return namesOf(MovieSearch.search(List.of(movie), criteria, EVERYTHING_ON_ONE_PAGE));
  }

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
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(0, 20));

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
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(0, 2));

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
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(2, 2));

      // then
      assertThat(namesOf(page)).containsExactly("Movie 005");
      assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void shouldReturnFullLastPageWhenItemsFillItExactly() {
      // given
      List<Movie> movies = numberedMovies(4);

      // when
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(1, 2));

      // then
      assertThat(namesOf(page)).containsExactly("Movie 003", "Movie 004");
      assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyItemsWithTrueTotalsWhenPageIsPastTheEnd() {
      // given
      List<Movie> movies = numberedMovies(5);

      // when
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(3, 2));

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
      MoviePage page =
          MovieSearch.search(
              movies, new MovieSearchCriteria.All(), new MoviePageRequest(Integer.MAX_VALUE, 100));

      // then
      assertThat(page.items()).isEmpty();
      assertThat(page.totalItems()).isEqualTo(5);
    }

    @Test
    void shouldReturnHundredItemsAndTwoPagesWhenSizeIsMaximumAndCatalogHasHundredFifty() {
      // given
      List<Movie> movies = numberedMovies(150);

      // when
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(0, 100));

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
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(1, 3));

      // then
      assertThat(page.page()).isEqualTo(1);
      assertThat(page.size()).isEqualTo(3);
    }

    @Test
    void shouldEchoRequestedPageAndSizeWhenPageIsPastTheEnd() {
      // given
      List<Movie> movies = numberedMovies(1);

      // when
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(7, 4));

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
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(0, 20));

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
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(0, 20));

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
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(0, 20));

      // then
      assertThat(page.items()).containsExactly(spaced, single);
    }

    @Test
    void shouldOrderBeforePagingWhenInputIsUnsorted() {
      // given
      List<Movie> movies = new ArrayList<>(numberedMovies(5));
      Collections.reverse(movies);

      // when
      MoviePage page =
          MovieSearch.search(movies, new MovieSearchCriteria.All(), new MoviePageRequest(1, 2));

      // then
      assertThat(namesOf(page)).containsExactly("Movie 003", "Movie 004");
    }
  }

  @Nested
  class ByName {

    @Test
    void shouldMatchNameWhenTextIsInTheMiddle() {
      // given
      MovieSearchCriteria criteria = new NameContains("runner");

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Blade Runner 2049");
    }

    @Test
    void shouldMatchNameWhenTextIsAtTheEnd() {
      // given
      MovieSearchCriteria criteria = new NameContains("water");

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("The Shape of Water");
    }

    @Test
    void shouldNotMatchNameWhenMovieNameHasNonBreakingSpaceInsteadOfSpace() {
      // given
      Movie movie = aMovie().withName("Star\u00A0Wars").buildMovie();

      // when
      List<String> names = namesMatching(movie, new NameContains("star wars"));

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldMatchNameWhenTextIsTheStart() {
      // given
      MovieSearchCriteria criteria = new NameContains("alien");

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Aliens");
    }

    @Test
    void shouldMatchNameIgnoringCaseWhenTextHasOtherCase() {
      // given
      MovieSearchCriteria criteria = new NameContains("BLADE runner");

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Blade Runner 2049");
    }

    @Test
    void shouldIgnoreExtraSpacesWhenTextHasThem() {
      // given
      MovieSearchCriteria criteria = new NameContains("  blade    runner ");

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Blade Runner 2049");
    }

    @Test
    void shouldMatchNameWhenStoredNameHasExtraSpaces() {
      // given
      Movie movie = aMovie().withName("The   Matrix ").buildMovie();

      // when
      List<String> names = namesMatching(movie, new NameContains("the matrix"));

      // then
      assertThat(names).containsExactly("The   Matrix ");
    }

    @Test
    void shouldReturnEmptyPageWithZeroTotalsWhenNoNameMatches() {
      // given
      MovieSearchCriteria criteria = new NameContains("zebra");

      // when
      MoviePage page = MovieSearch.search(CATALOG, criteria, EVERYTHING_ON_ONE_PAGE);

      // then
      assertThat(page.items()).isEmpty();
      assertThat(page.totalItems()).isZero();
      assertThat(page.totalPages()).isZero();
    }

    @Test
    void shouldNotFoldAccentsWhenNameHasAccent() {
      // given
      Movie movie = aMovie().withName("Amélie").buildMovie();

      // when
      List<String> names = namesMatching(movie, new NameContains("amelie"));

      // then
      assertThat(names).isEmpty();
    }
  }

  @Nested
  class ByYear {

    @Test
    void shouldMatchMoviesFromMinYearOnwardsWhenOnlyMinYearIsGiven() {
      // given
      MovieSearchCriteria criteria = new YearRange(2017, Integer.MAX_VALUE);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names)
          .containsExactlyInAnyOrder("Ad Astra", "Blade Runner 2049", "The Shape of Water");
    }

    @Test
    void shouldMatchMoviesUpToMaxYearWhenOnlyMaxYearIsGiven() {
      // given
      MovieSearchCriteria criteria = new YearRange(Integer.MIN_VALUE, 1986);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Aliens", "Raiders of the Lost Ark", "Bare");
    }

    @Test
    void shouldMatchMoviesWithinBothBoundsInclusiveWhenBothAreGiven() {
      // given
      MovieSearchCriteria criteria = new YearRange(2001, 2011);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Spirited Away", "Drive");
    }

    @Test
    void shouldMatchOnlyThatYearWhenBoundsAreEqual() {
      // given
      MovieSearchCriteria criteria = new YearRange(2017, 2017);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Blade Runner 2049", "The Shape of Water");
    }

    @Test
    void shouldExcludeMoviesWhenYearIsOneOutsideTheBounds() {
      // given
      MovieSearchCriteria criteria = new YearRange(1982, 2010);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Aliens", "Spirited Away");
    }

    @Test
    void shouldReturnEmptyPageWithZeroTotalsWhenRangeIsOutsideTheData() {
      // given
      MovieSearchCriteria criteria = new YearRange(2030, 2040);

      // when
      MoviePage page = MovieSearch.search(CATALOG, criteria, EVERYTHING_ON_ONE_PAGE);

      // then
      assertThat(page.items()).isEmpty();
      assertThat(page.totalItems()).isZero();
      assertThat(page.totalPages()).isZero();
    }
  }

  @Nested
  class ByGenre {

    @Test
    void shouldMatchEveryMovieWhenOneOfItsGenresEqualsText() {
      // given
      MovieSearchCriteria criteria = new Genre("Drama");

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names)
          .containsExactlyInAnyOrder(
              "Ad Astra", "Blade Runner 2049", "Drive", "The Shape of Water");
    }

    @Test
    void shouldMatchGenreIgnoringCaseWhenTextHasOtherCase() {
      // given
      MovieSearchCriteria criteria = new Genre("SCI-FI");

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Aliens");
    }

    @Test
    void shouldIgnoreExtraSpacesWhenGenreTextHasThem() {
      // given
      MovieSearchCriteria criteria = new Genre("   sci-fi  ");

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Aliens");
    }

    @Test
    void shouldNotMatchGenreWhenTextIsOnlyPartOfIt() {
      // given
      MovieSearchCriteria criteria = new Genre("sci");

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotMatchGenreWhenTextIsLongerThanIt() {
      // given
      MovieSearchCriteria criteria = new Genre("dramatic");

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).isEmpty();
    }
  }

  @Nested
  class ByViewerAge {

    @Test
    void shouldIncludeMovieWhenAgeLimitEqualsViewerAge() {
      // given
      MovieSearchCriteria criteria = new ViewerAge(12);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names)
          .containsExactlyInAnyOrder(
              "Ad Astra", "Raiders of the Lost Ark", "Spirited Away", "Bare");
    }

    @Test
    void shouldExcludeMovieWhenAgeLimitIsOneAboveViewerAge() {
      // given
      MovieSearchCriteria criteria = new ViewerAge(11);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names)
          .containsExactlyInAnyOrder("Raiders of the Lost Ark", "Spirited Away", "Bare");
    }

    @Test
    void shouldReturnOnlyMoviesWithoutAgeLimitWhenViewerAgeIsZero() {
      // given
      MovieSearchCriteria criteria = new ViewerAge(0);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Raiders of the Lost Ark", "Spirited Away");
    }

    @Test
    void shouldReturnEveryMovieWhenViewerAgeIsHighest() {
      // given
      MovieSearchCriteria criteria = new ViewerAge(ViewerAge.OLDEST);

      // when
      MoviePage page = MovieSearch.search(CATALOG, criteria, EVERYTHING_ON_ONE_PAGE);

      // then
      assertThat(page.totalItems()).isEqualTo(CATALOG.size());
    }
  }

  @Nested
  class ByRating {

    @Test
    void shouldMatchRatingsFromMinOnwardsWhenOnlyMinRatingIsGiven() {
      // given
      MovieSearchCriteria criteria = new RatingRange(5, RatingRange.HIGHEST);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Aliens", "Blade Runner 2049", "Spirited Away");
    }

    @Test
    void shouldMatchRatingsUpToMaxWhenOnlyMaxRatingIsGiven() {
      // given
      MovieSearchCriteria criteria = new RatingRange(RatingRange.LOWEST, 3);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Ad Astra", "Bare");
    }

    @Test
    void shouldMatchRatingsWithinBothBoundsInclusiveWhenBothAreGiven() {
      // given
      MovieSearchCriteria criteria = new RatingRange(3, 4);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names)
          .containsExactlyInAnyOrder(
              "Ad Astra", "Drive", "Raiders of the Lost Ark", "The Shape of Water");
    }

    @Test
    void shouldMatchOnlyThatRatingWhenBoundsAreEqual() {
      // given
      MovieSearchCriteria criteria = new RatingRange(4, 4);

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names)
          .containsExactlyInAnyOrder("Drive", "Raiders of the Lost Ark", "The Shape of Water");
    }

    @Test
    void shouldReturnEmptyPageWhenNoMovieHasTheRating() {
      // given
      List<Movie> movies = List.of(AD_ASTRA, DRIVE);
      MovieSearchCriteria criteria = new RatingRange(5, 5);

      // when
      MoviePage page = MovieSearch.search(movies, criteria, EVERYTHING_ON_ONE_PAGE);

      // then
      assertThat(page.items()).isEmpty();
      assertThat(page.totalItems()).isZero();
    }
  }

  @Nested
  class ByDirector {

    @Test
    void shouldMatchDirectorWhenOnlyLastNameIsGiven() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("grey"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldMatchEveryDirectorWithThatFirstNameWhenOnlyFirstNameIsGiven() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("james"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Ad Astra", "Aliens");
    }

    @Test
    void shouldMatchDirectorWhenFirstAndLastNameAreGiven() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("james", "grey"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldMatchDirectorWhenWordsAreInReversedOrder() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("grey", "james"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldMatchDirectorIgnoringCaseWhenWordsHaveOtherCase() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("SPIELBERG"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Raiders of the Lost Ark");
    }

    @Test
    void shouldMatchMultiWordLastNameWhenEveryWordIsGiven() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("del", "toro"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("The Shape of Water");
    }

    @Test
    void shouldMatchMultiWordLastNameWhenWordsAreGivenWithFirstName() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("guillermo", "toro"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("The Shape of Water");
    }

    @Test
    void shouldMatchWordsContainedInNameWhenWordIsOnlyPartOfIt() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("spiel"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Raiders of the Lost Ark");
    }

    @Test
    void shouldNotMatchDirectorWhenOneWordIsMissing() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("james", "spielberg"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotMatchWhenMovieHasNoDirector() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("james"));

      // when
      List<String> names = namesMatching(BARE, criteria);

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotMatchDirectorWhenNameIsGivenAsOneWordWithNonBreakingSpace() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("james\u00A0grey"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotFoldAccentsWhenDirectorNameHasAccent() {
      // given
      Movie movie = aMovie().withDirector(aPerson("José", "Padilha")).buildMovie();

      // when
      List<String> names = namesMatching(movie, new DirectorNamed(List.of("jose")));

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotMatchDirectorWhenWordIsOnlyInAnActorName() {
      // given
      MovieSearchCriteria criteria = new DirectorNamed(List.of("pitt"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).isEmpty();
    }
  }

  @Nested
  class ByActor {

    @Test
    void shouldMatchActorWhenOnlyFirstNameIsGiven() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("brad"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldMatchActorWhenOnlyLastNameIsGiven() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("pitt"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldMatchEveryMovieWithThatActorWhenFirstAndLastNameAreGiven() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("harrison", "ford"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Blade Runner 2049", "Raiders of the Lost Ark");
    }

    @Test
    void shouldMatchActorWhenWordsAreInReversedOrder() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("ford", "harrison"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Blade Runner 2049", "Raiders of the Lost Ark");
    }

    @Test
    void shouldMatchActorIgnoringCaseWhenWordsHaveOtherCase() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("GOSLING"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Blade Runner 2049", "Drive");
    }

    @Test
    void shouldMatchMultiWordLastNameWhenEveryWordIsGiven() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("lee", "jones"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldMatchMultiWordLastNameWhenFirstNameIsGivenWithIt() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("tommy", "lee", "jones"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldNotMatchWhenWordsBelongToDifferentActors() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("ryan", "wright"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotMatchWhenFirstNameAndLastNameBelongToDifferentActorsOfOneMovie() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("robin", "gosling"));

      // when
      List<String> names = namesMatching(BLADE_RUNNER, criteria);

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotMatchActorWhenOneWordIsMissing() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("harrison", "gosling"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotMatchWhenMovieHasNoActors() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("ford"));

      // when
      List<String> names = namesMatching(BARE, criteria);

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotMatchActorWhenNameIsGivenAsOneWordWithNonBreakingSpace() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("harrison\u00A0ford"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotFoldAccentsWhenActorNameHasAccent() {
      // given
      Movie movie = aMovie().withActors(List.of(aPerson("Mélanie", "Laurent"))).buildMovie();

      // when
      List<String> names = namesMatching(movie, new ActorNamed(List.of("melanie")));

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldNotMatchActorWhenWordIsOnlyInTheDirectorName() {
      // given
      MovieSearchCriteria criteria = new ActorNamed(List.of("spielberg"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).isEmpty();
    }
  }

  @Nested
  class BySynopsis {

    @Test
    void shouldMatchSynopsisWhenSingleWordIsInIt() {
      // given
      MovieSearchCriteria criteria = new SynopsisContains(List.of("missing"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Ad Astra", "Blade Runner 2049");
    }

    @Test
    void shouldMatchSynopsisIgnoringCaseWhenWordHasOtherCase() {
      // given
      MovieSearchCriteria criteria = new SynopsisContains(List.of("MCBRIDE"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldMatchSynopsisWhenWordsAreInTheSameOrderAsInTheText() {
      // given
      MovieSearchCriteria criteria = new SynopsisContains(List.of("missing", "father"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldMatchSynopsisWhenWordsAreInAnyOrder() {
      // given
      MovieSearchCriteria criteria = new SynopsisContains(List.of("father", "missing"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldMatchSynopsisWhenWordsAreFarApart() {
      // given
      MovieSearchCriteria criteria = new SynopsisContains(List.of("astronaut", "expedition"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactly("Ad Astra");
    }

    @Test
    void shouldNotMatchSynopsisWhenOneWordIsMissing() {
      // given
      MovieSearchCriteria criteria = new SynopsisContains(List.of("missing", "zebra"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).isEmpty();
    }

    @Test
    void shouldMatchSynopsisWhenWordIsOnlyPartOfOneOfItsWords() {
      // given
      MovieSearchCriteria criteria = new SynopsisContains(List.of("miss"));

      // when
      List<String> names = namesMatching(criteria);

      // then
      assertThat(names).containsExactlyInAnyOrder("Ad Astra", "Blade Runner 2049");
    }

    @Test
    void shouldNotMatchWhenMovieHasNoSynopsis() {
      // given
      MovieSearchCriteria criteria = new SynopsisContains(List.of("missing"));

      // when
      List<String> names = namesMatching(BARE, criteria);

      // then
      assertThat(names).isEmpty();
    }
  }

  @Nested
  class FilteringWithPaging {

    @Test
    void shouldCountOnlyMatchesInTotalsWhenSearchFiltersMovies() {
      // given
      MovieSearchCriteria criteria = new Genre("drama");

      // when
      MoviePage page = MovieSearch.search(CATALOG, criteria, new MoviePageRequest(0, 2));

      // then
      assertThat(page.totalItems()).isEqualTo(4);
      assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldFilterBeforePagingWhenSearchAndPageAreGiven() {
      // given
      MovieSearchCriteria criteria = new Genre("drama");

      // when
      MoviePage page = MovieSearch.search(CATALOG, criteria, new MoviePageRequest(1, 3));

      // then
      assertThat(namesOf(page)).containsExactly("The Shape of Water");
    }

    @Test
    void shouldReturnMatchesInNameOrderWhenCatalogIsUnsorted() {
      // given
      MovieSearchCriteria criteria = new Genre("drama");

      // when
      MoviePage page = MovieSearch.search(CATALOG, criteria, new MoviePageRequest(0, 2));

      // then
      assertThat(namesOf(page)).containsExactly("Ad Astra", "Blade Runner 2049");
    }

    @Test
    void shouldReturnEmptyItemsWithTrueTotalsWhenPageIsPastTheLastMatch() {
      // given
      MovieSearchCriteria criteria = new Genre("drama");

      // when
      MoviePage page = MovieSearch.search(CATALOG, criteria, new MoviePageRequest(5, 2));

      // then
      assertThat(page.items()).isEmpty();
      assertThat(page.totalItems()).isEqualTo(4);
      assertThat(page.totalPages()).isEqualTo(2);
    }
  }

  @Test
  void shouldNotModifyInputListWhenSorting() {
    // given
    List<Movie> movies = new ArrayList<>(List.of(movieNamed("b"), movieNamed("a")));
    List<Movie> unmodifiableMovies = Collections.unmodifiableList(movies);
    List<Movie> before = List.copyOf(movies);

    // when
    MovieSearch.search(
        unmodifiableMovies, new MovieSearchCriteria.All(), new MoviePageRequest(0, 20));

    // then
    assertThat(movies).containsExactlyElementsOf(before);
  }
}

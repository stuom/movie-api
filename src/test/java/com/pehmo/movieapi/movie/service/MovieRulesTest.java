package com.pehmo.movieapi.movie.service;

import static com.pehmo.movieapi.movie.service.MovieTestData.aNewMovie;
import static com.pehmo.movieapi.movie.service.MovieTestData.aPerson;
import static com.pehmo.movieapi.movie.service.MovieTestData.textOfLength;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MovieRulesTest {

  /** Fixed clock: the latest allowed year is 2026 + 5 = 2031. */
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);

  private static final String NO_CONTROL_CHARACTERS = "must not contain control characters";

  private final MovieRules rules = new MovieRules(FIXED_CLOCK);

  /** Runs the rules on an invalid movie and returns the violated field paths in order. */
  private List<String> violatedFieldsOf(NewMovie newMovie) {
    Throwable thrown = catchThrowable(() -> rules.toValidMovie(newMovie));
    assertThat(thrown).isInstanceOf(InvalidMovieException.class);
    return ((InvalidMovieException) thrown)
        .violations().stream().map(FieldViolation::field).toList();
  }

  /** Runs the rules on an invalid movie and returns the exact violations in order. */
  private List<FieldViolation> violationsOf(NewMovie newMovie) {
    Throwable thrown = catchThrowable(() -> rules.toValidMovie(newMovie));
    assertThat(thrown).isInstanceOf(InvalidMovieException.class);
    return ((InvalidMovieException) thrown).violations();
  }

  private static List<String> genresOfSize(int size) {
    List<String> genres = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      genres.add("Genre" + i);
    }
    return genres;
  }

  private static List<NewMovie.PersonInput> actorsOfSize(int size) {
    List<NewMovie.PersonInput> actors = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      actors.add(aPerson("First" + i, "Last" + i));
    }
    return actors;
  }

  @Nested
  class ValidMovie {

    @Test
    void shouldReturnMovieWithAllFieldsWhenInputIsValid() {
      // given
      NewMovie newMovie = aNewMovie().build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie)
          .isEqualTo(
              new Movie(
                  MovieId.derivedFrom("Aliens", 1986),
                  "Aliens",
                  1986,
                  List.of("Action", "Sci-fi"),
                  16,
                  5,
                  new Person("James", "Cameron"),
                  List.of(new Person("Sigourney", "Weaver")),
                  "Ellen Ripley is rescued by a deep salvage team."));
    }

    @Test
    void shouldDeriveIdFromTrimmedNameAndYearWhenInputIsValid() {
      // given
      NewMovie newMovie = aNewMovie().withName("  Star   Wars ").withYear(1977).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.id()).isEqualTo(MovieId.derivedFrom("Star   Wars", 1977));
    }

    @Test
    void shouldNotChangeMovieWhenInputListsAreMutatedAfterwards() {
      // given
      List<String> genres = new ArrayList<>(List.of("Action"));
      List<NewMovie.PersonInput> actors = new ArrayList<>(List.of(aPerson("Sigourney", "Weaver")));
      NewMovie newMovie = aNewMovie().withGenres(genres).withActors(actors).build();
      Movie movie = rules.toValidMovie(newMovie);

      // when
      genres.add("Horror");
      actors.add(aPerson("Bill", "Paxton"));

      // then
      assertThat(movie.genres()).containsExactly("Action");
      assertThat(movie.actors()).containsExactly(new Person("Sigourney", "Weaver"));
    }

    @Test
    void shouldNotChangeMovieWhenInputListsAreMutatedBeforeValidation() {
      // given
      List<String> genres = new ArrayList<>(List.of("Action"));
      List<NewMovie.PersonInput> actors = new ArrayList<>(List.of(aPerson("Sigourney", "Weaver")));
      NewMovie newMovie = aNewMovie().withGenres(genres).withActors(actors).build();
      genres.add("Horror");
      actors.add(aPerson("Bill", "Paxton"));

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.genres()).containsExactly("Action");
      assertThat(movie.actors()).containsExactly(new Person("Sigourney", "Weaver"));
    }

    @Test
    void shouldReturnUnmodifiableGenresWhenInputIsValid() {
      // given
      Movie movie = rules.toValidMovie(aNewMovie().build());

      // when / then
      assertThatThrownBy(() -> movie.genres().add("Horror"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnUnmodifiableActorsWhenInputIsValid() {
      // given
      Movie movie = rules.toValidMovie(aNewMovie().build());

      // when / then
      assertThatThrownBy(() -> movie.actors().add(new Person("Bill", "Paxton")))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  class Name {

    @ParameterizedTest
    @ValueSource(ints = {1, 200})
    void shouldAcceptNameWhenLengthIsWithinBounds(int length) {
      // given
      NewMovie newMovie = aNewMovie().withName(textOfLength(length)).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.name()).hasSize(length);
    }

    @Test
    void shouldRejectNameWhenItIsTooLong() {
      // given
      NewMovie newMovie = aNewMovie().withName(textOfLength(201)).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("name");
    }

    @Test
    void shouldCountLengthAfterTrimmingWhenNameHasSurroundingSpaces() {
      // given
      NewMovie newMovie = aNewMovie().withName("  " + textOfLength(200) + "  ").build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.name()).hasSize(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n", "\u2003"})
    void shouldRejectNameWhenItIsBlank(String blankName) {
      // given
      NewMovie newMovie = aNewMovie().withName(blankName).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("name");
    }

    @Test
    void shouldRejectNameWhenItIsNull() {
      // given
      NewMovie newMovie = aNewMovie().withName(null).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("name");
    }

    @Test
    void shouldTrimNameWhenItHasSurroundingSpaces() {
      // given
      NewMovie newMovie = aNewMovie().withName("  Aliens  ").build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.name()).isEqualTo("Aliens");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"\0", "\t", "\n", "\r", "\u001B", "\u001F", "\u007F", "\u0085", "\u009F"})
    void shouldRejectNameWhenItContainsAnInnerControlCharacter(String control) {
      // given
      NewMovie newMovie = aNewMovie().withName("Ali" + control + "ens").build();

      // when
      List<FieldViolation> violations = violationsOf(newMovie);

      // then
      assertThat(violations).containsExactly(new FieldViolation("name", NO_CONTROL_CHARACTERS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\0Aliens", "Aliens\u001B", "Aliens\u007F", "\u0085Aliens"})
    void shouldRejectNameWhenItEndsWithNonWhitespaceControlCharacter(String name) {
      // given a control character that String.strip() does not remove
      NewMovie newMovie = aNewMovie().withName(name).build();

      // when
      List<FieldViolation> violations = violationsOf(newMovie);

      // then
      assertThat(violations).containsExactly(new FieldViolation("name", NO_CONTROL_CHARACTERS));
    }

    @Test
    void shouldTrimSurroundingTabAndNewlineWhenNameHasThem() {
      // given
      NewMovie newMovie = aNewMovie().withName("\tAliens\n").build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.name()).isEqualTo("Aliens");
    }

    @Test
    void shouldReportLengthAndControlCharacterViolationsWhenNameIsTooLongAndHasControlCharacter() {
      // given
      NewMovie newMovie = aNewMovie().withName(textOfLength(200) + "\n" + "a").build();

      // when
      List<FieldViolation> violations = violationsOf(newMovie);

      // then
      assertThat(violations)
          .containsExactly(
              new FieldViolation("name", "must be at most 200 characters"),
              new FieldViolation("name", NO_CONTROL_CHARACTERS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ali ens", "Ali\u00A0ens"})
    void shouldAcceptNameWhenItContainsAnInnerSpaceOrNoBreakSpace(String name) {
      // given
      NewMovie newMovie = aNewMovie().withName(name).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.name()).isEqualTo(name);
    }

    @Test
    void shouldReportIsRequiredWhenNameIsMissing() {
      // given
      NewMovie newMovie = aNewMovie().withName(null).build();

      // when / then
      assertThatThrownBy(() -> rules.toValidMovie(newMovie))
          .isInstanceOfSatisfying(
              InvalidMovieException.class,
              exception ->
                  assertThat(exception.violations())
                      .containsExactly(new FieldViolation("name", "is required")));
    }
  }

  @Nested
  class Year {

    @ParameterizedTest
    @ValueSource(ints = {1888, 2031})
    void shouldAcceptYearWhenItIsOnTheBoundary(int year) {
      // given
      NewMovie newMovie = aNewMovie().withYear(year).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.year()).isEqualTo(year);
    }

    @ParameterizedTest
    @ValueSource(ints = {1887, 2032})
    void shouldRejectYearWhenItIsOutsideTheBoundary(int year) {
      // given
      NewMovie newMovie = aNewMovie().withYear(year).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("year");
    }

    @Test
    void shouldReportClockBasedRangeWhenYearIsTooLate() {
      // given
      NewMovie newMovie = aNewMovie().withYear(2032).build();

      // when / then
      assertThatThrownBy(() -> rules.toValidMovie(newMovie))
          .isInstanceOfSatisfying(
              InvalidMovieException.class,
              exception ->
                  assertThat(exception.violations())
                      .containsExactly(
                          new FieldViolation("year", "must be between 1888 and 2031")));
    }

    @Test
    void shouldRejectYearWhenItIsNull() {
      // given
      NewMovie newMovie = aNewMovie().withYear(null).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("year");
    }
  }

  @Nested
  class Genres {

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void shouldAcceptGenresWhenCountIsWithinBounds(int count) {
      // given
      NewMovie newMovie = aNewMovie().withGenres(genresOfSize(count)).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.genres()).hasSize(count);
    }

    @Test
    void shouldRejectGenresWhenListIsEmpty() {
      // given
      NewMovie newMovie = aNewMovie().withGenres(List.of()).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("genres");
    }

    @Test
    void shouldRejectGenresWhenThereAreSix() {
      // given
      NewMovie newMovie = aNewMovie().withGenres(genresOfSize(6)).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("genres");
    }

    @Test
    void shouldNotCheckEntriesWhenThereAreTooManyGenres() {
      // given
      List<String> blankGenres = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        blankGenres.add(" ");
      }
      NewMovie newMovie = aNewMovie().withGenres(blankGenres).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("genres");
    }

    @Test
    void shouldRejectGenresWhenListIsNull() {
      // given
      NewMovie newMovie = aNewMovie().withGenres(null).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("genres");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 50})
    void shouldAcceptGenreWhenLengthIsWithinBounds(int length) {
      // given
      NewMovie newMovie = aNewMovie().withGenres(List.of(textOfLength(length))).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.genres()).containsExactly(textOfLength(length));
    }

    @Test
    void shouldRejectGenreWhenItIsTooLong() {
      // given
      NewMovie newMovie = aNewMovie().withGenres(Arrays.asList("Action", textOfLength(51))).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("genres[1]");
    }

    @Test
    void shouldRejectGenreWhenItIsBlank() {
      // given
      NewMovie newMovie = aNewMovie().withGenres(Arrays.asList("Action", "   ")).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("genres[1]");
    }

    @Test
    void shouldRejectGenreWhenItIsNull() {
      // given
      NewMovie newMovie = aNewMovie().withGenres(Arrays.asList(null, "Action")).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("genres[0]");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"\0", "\t", "\n", "\r", "\u001B", "\u001F", "\u007F", "\u0085", "\u009F"})
    void shouldRejectGenreWhenItContainsAnInnerControlCharacter(String control) {
      // given
      NewMovie newMovie = aNewMovie().withGenres(List.of("Action", "Sci" + control + "fi")).build();

      // when
      List<FieldViolation> violations = violationsOf(newMovie);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("genres[1]", NO_CONTROL_CHARACTERS));
    }

    @Test
    void shouldAllowGenresThatDifferOnlyInCase() {
      // given
      NewMovie newMovie = aNewMovie().withGenres(List.of("Sci-fi", "SCI-FI")).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.genres()).containsExactly("Sci-fi", "SCI-FI");
    }

    @Test
    void shouldTrimGenresWhenTheyHaveSurroundingSpaces() {
      // given
      NewMovie newMovie = aNewMovie().withGenres(List.of(" Action ", "\tSci-fi")).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.genres()).containsExactly("Action", "Sci-fi");
    }
  }

  @Nested
  class AgeLimit {

    @ParameterizedTest
    @ValueSource(ints = {0, 18})
    void shouldAcceptAgeLimitWhenItIsOnTheBoundary(int ageLimit) {
      // given
      NewMovie newMovie = aNewMovie().withAgeLimit(ageLimit).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.ageLimit()).isEqualTo(ageLimit);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 19})
    void shouldRejectAgeLimitWhenItIsOutsideTheBoundary(int ageLimit) {
      // given
      NewMovie newMovie = aNewMovie().withAgeLimit(ageLimit).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("ageLimit");
    }

    @Test
    void shouldRejectAgeLimitWhenItIsNull() {
      // given
      NewMovie newMovie = aNewMovie().withAgeLimit(null).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("ageLimit");
    }
  }

  @Nested
  class Rating {

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void shouldAcceptRatingWhenItIsOnTheBoundary(int rating) {
      // given
      NewMovie newMovie = aNewMovie().withRating(rating).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.rating()).isEqualTo(rating);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void shouldRejectRatingWhenItIsOutsideTheBoundary(int rating) {
      // given
      NewMovie newMovie = aNewMovie().withRating(rating).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("rating");
    }

    @Test
    void shouldRejectRatingWhenItIsNull() {
      // given
      NewMovie newMovie = aNewMovie().withRating(null).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("rating");
    }
  }

  @Nested
  class Director {

    @Test
    void shouldAcceptMovieWhenDirectorIsNull() {
      // given
      NewMovie newMovie = aNewMovie().withDirector(null).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.director()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void shouldAcceptDirectorNamesWhenLengthIsWithinBounds(int length) {
      // given
      NewMovie newMovie =
          aNewMovie().withDirector(aPerson(textOfLength(length), textOfLength(length))).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.director())
          .isEqualTo(new Person(textOfLength(length), textOfLength(length)));
    }

    @Test
    void shouldRejectDirectorFirstNameWhenItIsTooLong() {
      // given
      NewMovie newMovie = aNewMovie().withDirector(aPerson(textOfLength(101), "Cameron")).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("director.firstName");
    }

    @Test
    void shouldRejectDirectorLastNameWhenItIsTooLong() {
      // given
      NewMovie newMovie = aNewMovie().withDirector(aPerson("James", textOfLength(101))).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("director.lastName");
    }

    @Test
    void shouldRejectDirectorFirstNameWhenItIsBlank() {
      // given
      NewMovie newMovie = aNewMovie().withDirector(aPerson("   ", "Cameron")).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("director.firstName");
    }

    @Test
    void shouldRejectDirectorLastNameWhenItIsNull() {
      // given
      NewMovie newMovie = aNewMovie().withDirector(aPerson("James", null)).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("director.lastName");
    }

    @Test
    void shouldReportBothNamesWhenDirectorHasNoNames() {
      // given
      NewMovie newMovie = aNewMovie().withDirector(aPerson(null, null)).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("director.firstName", "director.lastName");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"\0", "\t", "\n", "\r", "\u001B", "\u001F", "\u007F", "\u0085", "\u009F"})
    void shouldRejectDirectorFirstNameWhenItContainsAnInnerControlCharacter(String control) {
      // given
      NewMovie newMovie =
          aNewMovie().withDirector(aPerson("Ja" + control + "mes", "Cameron")).build();

      // when
      List<FieldViolation> violations = violationsOf(newMovie);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("director.firstName", NO_CONTROL_CHARACTERS));
    }

    @Test
    void shouldTrimDirectorNamesWhenTheyHaveSurroundingSpaces() {
      // given
      NewMovie newMovie = aNewMovie().withDirector(aPerson(" Robert ", " Downey Jr. ")).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.director()).isEqualTo(new Person("Robert", "Downey Jr."));
    }
  }

  @Nested
  class Actors {

    @Test
    void shouldReturnEmptyActorsWhenListIsNull() {
      // given
      NewMovie newMovie = aNewMovie().withActors(null).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.actors()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 30})
    void shouldAcceptActorsWhenCountIsWithinBounds(int count) {
      // given
      NewMovie newMovie = aNewMovie().withActors(actorsOfSize(count)).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.actors()).hasSize(count);
    }

    @Test
    void shouldRejectActorsWhenThereAreThirtyOne() {
      // given
      NewMovie newMovie = aNewMovie().withActors(actorsOfSize(31)).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("actors");
    }

    @Test
    void shouldNotCheckEntriesWhenThereAreTooManyActors() {
      // given
      List<NewMovie.PersonInput> actors = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        actors.add(null);
      }
      NewMovie newMovie = aNewMovie().withActors(actors).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("actors");
    }

    @Test
    void shouldPreserveActorOrderWhenInputIsValid() {
      // given
      NewMovie newMovie =
          aNewMovie()
              .withActors(
                  List.of(aPerson("Zed", "Zulu"), aPerson("Al", "Alpha"), aPerson("Bo", "B")))
              .build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.actors())
          .containsExactly(
              new Person("Zed", "Zulu"), new Person("Al", "Alpha"), new Person("Bo", "B"));
    }

    @Test
    void shouldRejectActorWhenEntryIsNull() {
      // given
      NewMovie newMovie =
          aNewMovie().withActors(Arrays.asList(aPerson("Sigourney", "Weaver"), null)).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("actors[1]");
    }

    @Test
    void shouldReportActorLastNameWithIndexWhenItIsBlank() {
      // given
      NewMovie newMovie =
          aNewMovie()
              .withActors(List.of(aPerson("Sigourney", "Weaver"), aPerson("Bill", " ")))
              .build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("actors[1].lastName");
    }

    @Test
    void shouldReportActorFirstNameWithIndexWhenItIsTooLong() {
      // given
      NewMovie newMovie =
          aNewMovie().withActors(List.of(aPerson(textOfLength(101), "Weaver"))).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("actors[0].firstName");
    }

    @Test
    void shouldAcceptActorNamesWhenLengthIsOneOrOneHundred() {
      // given
      NewMovie newMovie = aNewMovie().withActors(List.of(aPerson("a", textOfLength(100)))).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.actors()).containsExactly(new Person("a", textOfLength(100)));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"\0", "\t", "\n", "\r", "\u001B", "\u001F", "\u007F", "\u0085", "\u009F"})
    void shouldRejectActorLastNameWhenItContainsAnInnerControlCharacter(String control) {
      // given
      NewMovie newMovie =
          aNewMovie().withActors(List.of(aPerson("Sigourney", "We" + control + "aver"))).build();

      // when
      List<FieldViolation> violations = violationsOf(newMovie);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("actors[0].lastName", NO_CONTROL_CHARACTERS));
    }

    @Test
    void shouldTrimActorNamesWhenTheyHaveSurroundingSpaces() {
      // given
      NewMovie newMovie =
          aNewMovie().withActors(List.of(aPerson(" Robert", " Downey Jr."))).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.actors()).containsExactly(new Person("Robert", "Downey Jr."));
    }
  }

  @Nested
  class Synopsis {

    @Test
    void shouldAcceptMovieWhenSynopsisIsNull() {
      // given
      NewMovie newMovie = aNewMovie().withSynopsis(null).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.synopsis()).isNull();
    }

    @Test
    void shouldAcceptSynopsisWhenItHas2000Characters() {
      // given
      NewMovie newMovie = aNewMovie().withSynopsis(textOfLength(2000)).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.synopsis()).hasSize(2000);
    }

    @Test
    void shouldRejectSynopsisWhenItHas2001Characters() {
      // given
      NewMovie newMovie = aNewMovie().withSynopsis(textOfLength(2001)).build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields).containsExactly("synopsis");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t", "\u2003"})
    void shouldReturnNullSynopsisWhenItIsBlank(String blankSynopsis) {
      // given
      NewMovie newMovie = aNewMovie().withSynopsis(blankSynopsis).build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.synopsis()).isNull();
    }

    @Test
    void shouldKeepInnerLineBreakWhenSynopsisHasOne() {
      // given
      NewMovie newMovie = aNewMovie().withSynopsis("First line.\nSecond line.").build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.synopsis()).isEqualTo("First line.\nSecond line.");
    }

    @Test
    void shouldTrimSynopsisWhenItHasSurroundingSpaces() {
      // given
      NewMovie newMovie = aNewMovie().withSynopsis("  A deep salvage team.  ").build();

      // when
      Movie movie = rules.toValidMovie(newMovie);

      // then
      assertThat(movie.synopsis()).isEqualTo("A deep salvage team.");
    }
  }

  @Nested
  class SeveralViolations {

    @Test
    void shouldReportAllViolationsInFieldOrderWhenSeveralFieldsAreInvalid() {
      // given
      NewMovie newMovie =
          aNewMovie()
              .withSynopsis(textOfLength(2001))
              .withActors(List.of(aPerson("Bill", "")))
              .withDirector(aPerson(null, "Cameron"))
              .withRating(9)
              .withAgeLimit(40)
              .withGenres(List.of())
              .withYear(1700)
              .withName(" ")
              .build();

      // when
      List<String> fields = violatedFieldsOf(newMovie);

      // then
      assertThat(fields)
          .containsExactly(
              "name",
              "year",
              "genres",
              "ageLimit",
              "rating",
              "director.firstName",
              "actors[0].lastName",
              "synopsis");
    }
  }
}

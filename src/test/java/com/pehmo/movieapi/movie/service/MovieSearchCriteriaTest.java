package com.pehmo.movieapi.movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pehmo.movieapi.movie.service.MovieSearchCriteria.ActorNamed;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.All;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.DirectorNamed;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.Genre;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.NameContains;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.RatingRange;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.SynopsisContains;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.ViewerAge;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.YearRange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MovieSearchCriteriaTest {

  private static final String COMBINED =
      "must not be combined with another search field; received: ";

  /** Builds request parameters from name and value pairs, in the order given. */
  private static Map<String, List<String>> parameters(String... namesAndValues) {
    Map<String, List<String>> parameters = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      parameters.put(namesAndValues[i], List.of(namesAndValues[i + 1]));
    }
    return parameters;
  }

  private static List<FieldViolation> violationsOf(Map<String, List<String>> parameters) {
    try {
      MovieSearchCriteria.of(parameters);
    } catch (InvalidQueryException e) {
      return e.violations();
    }
    throw new AssertionError("Expected InvalidQueryException");
  }

  /** One search field as a client sends it: its parameters, and how the error message names it. */
  private record SearchField(String label, Map<String, String> parameters) {

    static SearchField of(String... namesAndValues) {
      Map<String, String> parameters = new LinkedHashMap<>();
      List<String> names = new ArrayList<>();
      for (int i = 0; i < namesAndValues.length; i += 2) {
        parameters.put(namesAndValues[i], namesAndValues[i + 1]);
        names.add(namesAndValues[i]);
      }
      return new SearchField(String.join(" + ", names), parameters);
    }
  }

  private static final SearchField NAME_FIELD = SearchField.of("name", "alien");
  private static final SearchField YEAR_FIELD = SearchField.of("minYear", "2000");
  private static final SearchField GENRE_FIELD = SearchField.of("genre", "drama");
  private static final SearchField VIEWER_AGE_FIELD = SearchField.of("viewerAge", "12");
  private static final SearchField RATING_FIELD = SearchField.of("minRating", "3");
  private static final SearchField DIRECTOR_FIELD = SearchField.of("director", "spielberg");
  private static final SearchField ACTOR_FIELD = SearchField.of("actor", "ford");
  private static final SearchField SYNOPSIS_FIELD = SearchField.of("synopsis", "missing");

  /** The eight search fields in contract-table order, one representative parameter each. */
  private static final List<SearchField> FIELDS =
      List.of(
          NAME_FIELD,
          YEAR_FIELD,
          GENRE_FIELD,
          VIEWER_AGE_FIELD,
          RATING_FIELD,
          DIRECTOR_FIELD,
          ACTOR_FIELD,
          SYNOPSIS_FIELD);

  private static final List<SearchField> YEAR_VARIANTS =
      List.of(
          SearchField.of("minYear", "2000"),
          SearchField.of("maxYear", "2009"),
          SearchField.of("minYear", "2000", "maxYear", "2009"));

  private static final List<SearchField> RATING_VARIANTS =
      List.of(
          SearchField.of("minRating", "3"),
          SearchField.of("maxRating", "4"),
          SearchField.of("minRating", "3", "maxRating", "4"));

  private static Map<String, List<String>> parametersOf(List<SearchField> fields) {
    Map<String, List<String>> parameters = new LinkedHashMap<>();
    for (SearchField field : fields) {
      field.parameters().forEach((name, value) -> parameters.put(name, List.of(value)));
    }
    return parameters;
  }

  /** One violation per received parameter, in table order, all naming the fields received. */
  private static List<FieldViolation> expectedOneFieldViolations(List<SearchField> fields) {
    String received = String.join(", ", fields.stream().map(SearchField::label).toList());
    List<FieldViolation> violations = new ArrayList<>();
    for (SearchField field : fields) {
      for (String parameterName : field.parameters().keySet()) {
        violations.add(new FieldViolation(parameterName, COMBINED + received));
      }
    }
    return violations;
  }

  static Stream<Arguments> everyPairOfSearchFields() {
    List<Arguments> pairs = new ArrayList<>();
    for (int first = 0; first < FIELDS.size(); first++) {
      for (int second = first + 1; second < FIELDS.size(); second++) {
        pairs.add(
            Arguments.of(
                FIELDS.get(first).label() + " + " + FIELDS.get(second).label(),
                List.of(FIELDS.get(first), FIELDS.get(second))));
      }
    }
    return pairs.stream();
  }

  static Stream<Arguments> pairsWithSingleAndBothBounds() {
    List<Arguments> pairs = new ArrayList<>();
    for (SearchField year : YEAR_VARIANTS) {
      pairs.add(Arguments.of(year.label() + " and genre", List.of(year, GENRE_FIELD)));
      pairs.add(Arguments.of(year.label() + " and director", List.of(year, DIRECTOR_FIELD)));
    }
    for (SearchField rating : RATING_VARIANTS) {
      pairs.add(Arguments.of("name and " + rating.label(), List.of(NAME_FIELD, rating)));
      pairs.add(Arguments.of(rating.label() + " and actor", List.of(rating, ACTOR_FIELD)));
    }
    for (SearchField year : YEAR_VARIANTS) {
      for (SearchField rating : RATING_VARIANTS) {
        pairs.add(Arguments.of(year.label() + " and " + rating.label(), List.of(year, rating)));
      }
    }
    return pairs.stream();
  }

  @Nested
  class NoSearchField {

    @Test
    void shouldReturnAllWhenNoParametersAreGiven() {
      // given
      Map<String, List<String>> parameters = Map.of();

      // when
      MovieSearchCriteria criteria = MovieSearchCriteria.of(parameters);

      // then
      assertThat(criteria).isEqualTo(new All());
    }

    @Test
    void shouldReturnAllWhenOnlyBlankValuesAreGiven() {
      // given
      Map<String, List<String>> parameters =
          parameters(
              "name",
              "",
              "minYear",
              "  ",
              "maxYear",
              " ",
              "genre",
              "   ",
              "viewerAge",
              "",
              "minRating",
              " ",
              "maxRating",
              "",
              "director",
              "  ",
              "actor",
              " ",
              "synopsis",
              "");

      // when
      MovieSearchCriteria criteria = MovieSearchCriteria.of(parameters);

      // then
      assertThat(criteria).isEqualTo(new All());
    }

    @Test
    void shouldIgnoreParametersThatAreNotSearchParameters() {
      // given
      Map<String, List<String>> parameters = parameters("page", "abc", "size", "-1", "zebra", "x");

      // when
      MovieSearchCriteria criteria = MovieSearchCriteria.of(parameters);

      // then
      assertThat(criteria).isEqualTo(new All());
    }
  }

  @Nested
  class OneSearchField {

    static Stream<Arguments> eachFieldAlone() {
      return Stream.of(
          Arguments.of("name", parameters("name", "alien"), new NameContains("alien")),
          Arguments.of(
              "name is normalized", parameters("name", "  ALIEN  S "), new NameContains("alien s")),
          Arguments.of(
              "minYear only",
              parameters("minYear", "2000"),
              new YearRange(2000, Integer.MAX_VALUE)),
          Arguments.of(
              "maxYear only",
              parameters("maxYear", "2009"),
              new YearRange(Integer.MIN_VALUE, 2009)),
          Arguments.of(
              "both year bounds",
              parameters("minYear", "2000", "maxYear", "2009"),
              new YearRange(2000, 2009)),
          Arguments.of(
              "equal year bounds",
              parameters("minYear", "1999", "maxYear", "1999"),
              new YearRange(1999, 1999)),
          Arguments.of(
              "negative year", parameters("minYear", "-5", "maxYear", "0"), new YearRange(-5, 0)),
          Arguments.of("genre is normalized", parameters("genre", " Sci-Fi "), new Genre("sci-fi")),
          Arguments.of("viewerAge", parameters("viewerAge", "12"), new ViewerAge(12)),
          Arguments.of("viewerAge is trimmed", parameters("viewerAge", " 12 "), new ViewerAge(12)),
          Arguments.of("minRating only", parameters("minRating", "4"), new RatingRange(4, 5)),
          Arguments.of("maxRating only", parameters("maxRating", "2"), new RatingRange(1, 2)),
          Arguments.of(
              "both rating bounds",
              parameters("minRating", "2", "maxRating", "4"),
              new RatingRange(2, 4)),
          Arguments.of(
              "equal rating bounds",
              parameters("minRating", "3", "maxRating", "3"),
              new RatingRange(3, 3)),
          Arguments.of(
              "director words are split and normalized",
              parameters("director", "  Steven   SPIELBERG "),
              new DirectorNamed(List.of("steven", "spielberg"))),
          Arguments.of(
              "actor words are split and normalized",
              parameters("actor", "Harrison Ford"),
              new ActorNamed(List.of("harrison", "ford"))),
          Arguments.of(
              "synopsis words are split",
              parameters("synopsis", "missing father"),
              new SynopsisContains(List.of("missing", "father"))),
          Arguments.of(
              "duplicate words are dropped in first-seen order",
              parameters("actor", "ford Harrison FORD"),
              new ActorNamed(List.of("ford", "harrison"))),
          Arguments.of(
              "non-breaking space is part of a word",
              parameters("actor", "harrison\u00A0ford"),
              new ActorNamed(List.of("harrison\u00A0ford"))),
          Arguments.of(
              "non-breaking space alone is not blank",
              parameters("name", "\u00A0"),
              new NameContains("\u00A0")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eachFieldAlone")
    void shouldBuildMatchingCriteriaWhenOneSearchFieldIsGiven(
        String label, Map<String, List<String>> parameters, MovieSearchCriteria expected) {
      // given the parameters of one search field

      // when
      MovieSearchCriteria criteria = MovieSearchCriteria.of(parameters);

      // then
      assertThat(criteria).isEqualTo(expected);
    }

    @Test
    void shouldAcceptSearchFieldWhenBlankTextIsGivenWithIt() {
      // given
      Map<String, List<String>> parameters = parameters("name", "   ", "genre", "drama");

      // when
      MovieSearchCriteria criteria = MovieSearchCriteria.of(parameters);

      // then
      assertThat(criteria).isEqualTo(new Genre("drama"));
    }
  }

  @Nested
  class SeveralSearchFields {

    @ParameterizedTest(name = "{0}")
    @MethodSource(
        "com.pehmo.movieapi.movie.service.MovieSearchCriteriaTest#everyPairOfSearchFields")
    void shouldRejectEveryPairOfSearchFieldsWithOneViolationPerReceivedParameter(
        String label, List<SearchField> fields) {
      // given
      Map<String, List<String>> parameters = parametersOf(fields);

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations).containsExactlyElementsOf(expectedOneFieldViolations(fields));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(
        "com.pehmo.movieapi.movie.service.MovieSearchCriteriaTest#pairsWithSingleAndBothBounds")
    void shouldRejectFieldsWhenYearOrRatingIsGivenWithOneOrBothBounds(
        String label, List<SearchField> fields) {
      // given
      Map<String, List<String>> parameters = parametersOf(fields);

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations).containsExactlyElementsOf(expectedOneFieldViolations(fields));
    }

    @Test
    void shouldNameEveryReceivedFieldWhenThreeSearchFieldsAreGiven() {
      // given
      Map<String, List<String>> parameters =
          parameters("synopsis", "missing", "genre", "drama", "viewerAge", "12");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      String message = COMBINED + "genre, viewerAge, synopsis";
      assertThat(violations)
          .containsExactly(
              new FieldViolation("genre", message),
              new FieldViolation("viewerAge", message),
              new FieldViolation("synopsis", message));
    }

    @Test
    void shouldJoinTheBoundsOfOneFieldWithPlusWhenListingReceivedFields() {
      // given
      Map<String, List<String>> parameters =
          parameters("genre", "drama", "maxYear", "2009", "minYear", "2000");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      String message = COMBINED + "minYear + maxYear, genre";
      assertThat(violations)
          .containsExactly(
              new FieldViolation("minYear", message),
              new FieldViolation("maxYear", message),
              new FieldViolation("genre", message));
    }

    @Test
    void shouldListReceivedFieldsInTableOrderWhenRequestOrderDiffers() {
      // given
      Map<String, List<String>> parameters = parameters("actor", "ford", "director", "spielberg");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      String message = COMBINED + "director, actor";
      assertThat(violations)
          .containsExactly(
              new FieldViolation("director", message), new FieldViolation("actor", message));
    }

    @Test
    void shouldNotCountBlankSearchFieldWhenTwoFieldsAreRejected() {
      // given
      Map<String, List<String>> parameters =
          parameters("name", " ", "genre", "drama", "actor", "ford");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      String message = COMBINED + "genre, actor";
      assertThat(violations)
          .containsExactly(
              new FieldViolation("genre", message), new FieldViolation("actor", message));
    }

    @Test
    void shouldNotRepeatClientValuesWhenFieldsAreCombined() {
      // given
      Map<String, List<String>> parameters =
          parameters("name", "secret-name", "genre", "secret-genre");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .extracting(FieldViolation::message)
          .noneMatch(message -> message.contains("secret"));
    }
  }

  @Nested
  class ViewerAgeRange {

    @ParameterizedTest(name = "viewerAge={0}")
    @ValueSource(ints = {0, 120})
    void shouldAcceptViewerAgeWhenItIsAtTheBoundaries(int age) {
      // given
      Map<String, List<String>> parameters = parameters("viewerAge", String.valueOf(age));

      // when
      MovieSearchCriteria criteria = MovieSearchCriteria.of(parameters);

      // then
      assertThat(criteria).isEqualTo(new ViewerAge(age));
    }

    @ParameterizedTest(name = "viewerAge={0}")
    @ValueSource(strings = {"-1", "121", "98765"})
    void shouldRejectViewerAgeWhenItIsOutsideZeroToHundredTwenty(String value) {
      // given
      Map<String, List<String>> parameters = parameters("viewerAge", value);

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("viewerAge", "must be between 0 and 120"));
    }

    @Test
    void shouldNotRepeatTheValueWhenViewerAgeIsOutOfRange() {
      // given
      Map<String, List<String>> parameters = parameters("viewerAge", "98765");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .extracting(FieldViolation::message)
          .noneMatch(message -> message.contains("98765"));
    }
  }

  @Nested
  class RatingBounds {

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("com.pehmo.movieapi.movie.service.MovieSearchCriteriaTest#acceptedRatingBounds")
    void shouldAcceptRatingWhenBoundIsOneOrFive(
        String parameter, String value, MovieSearchCriteria expected) {
      // given
      Map<String, List<String>> parameters = parameters(parameter, value);

      // when
      MovieSearchCriteria criteria = MovieSearchCriteria.of(parameters);

      // then
      assertThat(criteria).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("com.pehmo.movieapi.movie.service.MovieSearchCriteriaTest#rejectedRatingBounds")
    void shouldRejectRatingWhenBoundIsOutsideOneToFive(String parameter, String value) {
      // given
      Map<String, List<String>> parameters = parameters(parameter, value);

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation(parameter, "must be between 1 and 5"));
    }

    @Test
    void shouldRejectBothBoundsWhenBothAreOutsideOneToFive() {
      // given
      Map<String, List<String>> parameters = parameters("minRating", "0", "maxRating", "6");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(
              new FieldViolation("minRating", "must be between 1 and 5"),
              new FieldViolation("maxRating", "must be between 1 and 5"));
    }

    @Test
    void shouldReportOnlyMaxRatingWhenMaxRatingIsBelowOneAndMinRatingIsValid() {
      // given
      Map<String, List<String>> parameters = parameters("minRating", "3", "maxRating", "0");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("maxRating", "must be between 1 and 5"));
    }
  }

  static Stream<Arguments> acceptedRatingBounds() {
    return Stream.of(
        Arguments.of("minRating", "1", new RatingRange(1, 5)),
        Arguments.of("minRating", "5", new RatingRange(5, 5)),
        Arguments.of("maxRating", "1", new RatingRange(1, 1)),
        Arguments.of("maxRating", "5", new RatingRange(1, 5)));
  }

  static Stream<Arguments> rejectedRatingBounds() {
    return Stream.of(
        Arguments.of("minRating", "0"),
        Arguments.of("minRating", "6"),
        Arguments.of("maxRating", "0"),
        Arguments.of("maxRating", "6"));
  }

  @Nested
  class InvertedBounds {

    @Test
    void shouldRejectYearsWhenMinYearIsAboveMaxYear() {
      // given
      Map<String, List<String>> parameters = parameters("minYear", "2010", "maxYear", "2000");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("minYear", "must not be greater than maxYear"));
    }

    @Test
    void shouldAcceptYearsWhenBoundsAreEqual() {
      // given
      Map<String, List<String>> parameters = parameters("minYear", "2000", "maxYear", "2000");

      // when
      MovieSearchCriteria criteria = MovieSearchCriteria.of(parameters);

      // then
      assertThat(criteria).isEqualTo(new YearRange(2000, 2000));
    }

    @Test
    void shouldRejectRatingsWhenMinRatingIsAboveMaxRating() {
      // given
      Map<String, List<String>> parameters = parameters("minRating", "4", "maxRating", "2");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("minRating", "must not be greater than maxRating"));
    }

    @Test
    void shouldReportOnlyTheRangeErrorWhenRatingIsOutOfRangeAndInverted() {
      // given
      Map<String, List<String>> parameters = parameters("minRating", "6", "maxRating", "3");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("minRating", "must be between 1 and 5"));
    }

    @Test
    void shouldNotCompareYearsWhenMinYearIsNotWholeNumber() {
      // given
      Map<String, List<String>> parameters = parameters("minYear", "abc", "maxYear", "2000");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("minYear", "must be a whole number"));
    }
  }

  @Nested
  class BadValues {

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("com.pehmo.movieapi.movie.service.MovieSearchCriteriaTest#numericParameters")
    void shouldRejectNumberWhenValueIsNotWholeNumber(String parameter, String value) {
      // given
      Map<String, List<String>> parameters = parameters(parameter, value);

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation(parameter, "must be a whole number"));
    }

    @Test
    void shouldNotRepeatTheValueWhenNumberIsNotWholeNumber() {
      // given
      Map<String, List<String>> parameters = parameters("minYear", "secret-year");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .extracting(FieldViolation::message)
          .noneMatch(message -> message.contains("secret"));
    }

    @Test
    void shouldReportWholeNumberAndCombinedViolationsWhenNumberIsBadAndAnotherFieldIsGiven() {
      // given
      Map<String, List<String>> parameters = parameters("minRating", "abc", "genre", "x");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      String message = COMBINED + "genre, minRating";
      assertThat(violations)
          .containsExactly(
              new FieldViolation("minRating", "must be a whole number"),
              new FieldViolation("genre", message),
              new FieldViolation("minRating", message));
    }

    @Test
    void shouldReportRepeatedAndCombinedViolationsWhenParameterIsRepeatedAndAnotherFieldIsGiven() {
      // given
      Map<String, List<String>> parameters = new LinkedHashMap<>();
      parameters.put("actor", List.of("a", "b"));
      parameters.put("director", List.of("x"));

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      String message = COMBINED + "director, actor";
      assertThat(violations)
          .containsExactly(
              new FieldViolation("actor", "must be given only once"),
              new FieldViolation("director", message),
              new FieldViolation("actor", message));
    }

    @Test
    void shouldRejectTextParameterWhenItIsRepeated() {
      // given
      Map<String, List<String>> parameters = new LinkedHashMap<>();
      parameters.put("name", List.of("a", "b"));

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations).containsExactly(new FieldViolation("name", "must be given only once"));
    }

    @Test
    void shouldReportBlankRepeatedParameterOnceAndNotCountItAsSearchField() {
      // given
      Map<String, List<String>> parameters = new LinkedHashMap<>();
      parameters.put("name", List.of("", ""));
      parameters.put("genre", List.of("drama"));

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations).containsExactly(new FieldViolation("name", "must be given only once"));
    }

    @Test
    void shouldReportOnlyRepeatedMinYearWhenMaxYearIsGivenOnce() {
      // given
      Map<String, List<String>> parameters = new LinkedHashMap<>();
      parameters.put("minYear", List.of("1", "2"));
      parameters.put("maxYear", List.of("3"));

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("minYear", "must be given only once"));
    }

    @Test
    void shouldReportViolationsInTableOrderWhenSeveralParametersAreBad() {
      // given
      Map<String, List<String>> parameters = parameters("viewerAge", "999", "minYear", "abc");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      String message = COMBINED + "minYear, viewerAge";
      assertThat(violations)
          .containsExactly(
              new FieldViolation("minYear", "must be a whole number"),
              new FieldViolation("viewerAge", "must be between 0 and 120"),
              new FieldViolation("minYear", message),
              new FieldViolation("viewerAge", message));
    }
  }

  static Stream<Arguments> numericParameters() {
    return Stream.of("minYear", "maxYear", "viewerAge", "minRating", "maxRating")
        .flatMap(
            name ->
                Stream.of("abc", "1.5", "2147483648", "+5")
                    .map(value -> Arguments.of(name, value)));
  }

  @Nested
  class RecordInvariants {

    static Stream<Function<List<String>, MovieSearchCriteria>> wordRecords() {
      return Stream.of(DirectorNamed::new, ActorNamed::new, SynopsisContains::new);
    }

    @ParameterizedTest
    @MethodSource("wordRecords")
    void shouldRejectWordsWhenListIsEmpty(Function<List<String>, MovieSearchCriteria> create) {
      // given
      List<String> words = List.of();

      // when / then
      assertThatThrownBy(() -> create.apply(words)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("wordRecords")
    void shouldRejectWordsWhenOneWordIsBlank(Function<List<String>, MovieSearchCriteria> create) {
      // given
      List<String> words = List.of("ford", "  ");

      // when / then
      assertThatThrownBy(() -> create.apply(words)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("wordRecords")
    void shouldRejectWordsWhenOneWordContainsSpace(
        Function<List<String>, MovieSearchCriteria> create) {
      // given
      List<String> words = List.of("harrison ford");

      // when / then
      assertThatThrownBy(() -> create.apply(words)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("wordRecords")
    void shouldNormalizeWordsWhenRecordIsCreated(
        Function<List<String>, MovieSearchCriteria> create) {
      // given
      List<String> words = List.of("  FORD ", "Harrison");

      // when
      MovieSearchCriteria criteria = create.apply(words);

      // then
      assertThat(criteria).isEqualTo(create.apply(List.of("ford", "harrison")));
    }

    @ParameterizedTest
    @MethodSource("wordRecords")
    void shouldDropDuplicateWordsKeepingFirstSeenOrderWhenRecordIsCreated(
        Function<List<String>, MovieSearchCriteria> create) {
      // given
      List<String> words = List.of("ford", "Harrison", "FORD", "harrison", "ford");

      // when
      MovieSearchCriteria criteria = create.apply(words);

      // then
      assertThat(criteria).isEqualTo(create.apply(List.of("ford", "harrison")));
    }

    @Test
    void shouldRejectNameWhenTextIsBlank() {
      // given / when / then
      assertThatThrownBy(() -> new NameContains("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectGenreWhenTextIsBlank() {
      // given / when / then
      assertThatThrownBy(() -> new Genre("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNormalizeTextWhenNameRecordIsCreated() {
      // given
      String text = "  ALIEN ";

      // when
      NameContains criteria = new NameContains(text);

      // then
      assertThat(criteria).isEqualTo(new NameContains("alien"));
      assertThat(criteria.text()).isEqualTo("alien");
    }

    @Test
    void shouldNormalizeTextWhenGenreRecordIsCreated() {
      // given
      String text = " Sci-FI  ";

      // when
      Genre criteria = new Genre(text);

      // then
      assertThat(criteria.text()).isEqualTo("sci-fi");
    }

    @Test
    void shouldRejectYearRangeWhenMinIsAboveMax() {
      // given / when / then
      assertThatThrownBy(() -> new YearRange(2001, 2000))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 121})
    void shouldRejectViewerAgeWhenItIsOutsideZeroToHundredTwenty(int age) {
      // given / when / then
      assertThatThrownBy(() -> new ViewerAge(age)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("com.pehmo.movieapi.movie.service.MovieSearchCriteriaTest#invalidRatingRanges")
    void shouldRejectRatingRangeWhenBoundsAreOutsideOneToFiveOrInverted(int min, int max) {
      // given / when / then
      assertThatThrownBy(() -> new RatingRange(min, max))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  static Stream<Arguments> invalidRatingRanges() {
    return Stream.of(
        Arguments.of(0, 3), Arguments.of(1, 6), Arguments.of(4, 2), Arguments.of(6, 6));
  }

  @Test
  void shouldListSearchParametersInContractTableOrderWhenAskedForThem() {
    // given the contract table in AGENTS.md

    // when
    List<String> parameters = MovieSearchCriteria.PARAMETERS;

    // then
    assertThat(parameters)
        .containsExactly(
            "name",
            "minYear",
            "maxYear",
            "genre",
            "viewerAge",
            "minRating",
            "maxRating",
            "director",
            "actor",
            "synopsis");
  }
}

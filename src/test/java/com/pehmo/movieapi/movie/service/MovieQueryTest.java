package com.pehmo.movieapi.movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.pehmo.movieapi.movie.service.MovieSearchCriteria.ActorNamed;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.All;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.NameContains;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MovieQueryTest {

  private static Map<String, List<String>> parameters(String... namesAndValues) {
    Map<String, List<String>> parameters = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      parameters.put(namesAndValues[i], List.of(namesAndValues[i + 1]));
    }
    return parameters;
  }

  private static List<FieldViolation> violationsOf(Map<String, List<String>> parameters) {
    try {
      MovieQuery.parse(parameters);
    } catch (InvalidQueryException e) {
      return e.violations();
    }
    throw new AssertionError("Expected InvalidQueryException");
  }

  @Nested
  class Defaults {

    @Test
    void shouldUseDefaultPagingWhenNoParametersAreGiven() {
      // given
      Map<String, List<String>> parameters = Map.of();

      // when
      MovieQuery query = MovieQuery.parse(parameters);

      // then
      assertThat(query.paging()).isEqualTo(new MoviePageRequest(0, 20));
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", "  "})
    void shouldUseDefaultsWhenValuesAreBlank(String blank) {
      // given
      Map<String, List<String>> parameters = parameters("page", blank, "size", blank);

      // when
      MovieQuery query = MovieQuery.parse(parameters);

      // then
      assertThat(query.paging()).isEqualTo(new MoviePageRequest(0, 20));
    }

    @Test
    void shouldUseDefaultSizeWhenOnlyPageIsGiven() {
      // given
      Map<String, List<String>> parameters = parameters("page", "4");

      // when
      MovieQuery query = MovieQuery.parse(parameters);

      // then
      assertThat(query.paging()).isEqualTo(new MoviePageRequest(4, 20));
    }

    @Test
    void shouldUseDefaultPageWhenOnlySizeIsGiven() {
      // given
      Map<String, List<String>> parameters = parameters("size", "7");

      // when
      MovieQuery query = MovieQuery.parse(parameters);

      // then
      assertThat(query.paging()).isEqualTo(new MoviePageRequest(0, 7));
    }
  }

  @Nested
  class Page {

    @Test
    void shouldAcceptPageWhenItIsZero() {
      // given
      Map<String, List<String>> parameters = parameters("page", "0");

      // when
      MovieQuery query = MovieQuery.parse(parameters);

      // then
      assertThat(query.paging().page()).isZero();
    }

    @Test
    void shouldRejectPageWhenItIsNegative() {
      // given
      Map<String, List<String>> parameters = parameters("page", "-1");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations).containsExactly(new FieldViolation("page", "must be 0 or more"));
    }

    @Test
    void shouldTrimValueWhenPageHasSurroundingSpaces() {
      // given
      Map<String, List<String>> parameters = parameters("page", " 3 ");

      // when
      MovieQuery query = MovieQuery.parse(parameters);

      // then
      assertThat(query.paging().page()).isEqualTo(3);
    }

    @ParameterizedTest(name = "page={0}")
    @ValueSource(strings = {"abc", "1.5", "2147483648", "+5", "\u0661\u0662"})
    void shouldRejectPageWhenValueIsNotWholeNumber(String value) {
      // given
      Map<String, List<String>> parameters = parameters("page", value);

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations).containsExactly(new FieldViolation("page", "must be a whole number"));
    }
  }

  @Nested
  class Size {

    @ParameterizedTest(name = "size={0}")
    @ValueSource(ints = {1, 100})
    void shouldAcceptSizeWhenItIsAtTheBoundaries(int size) {
      // given
      Map<String, List<String>> parameters = parameters("size", String.valueOf(size));

      // when
      MovieQuery query = MovieQuery.parse(parameters);

      // then
      assertThat(query.paging().size()).isEqualTo(size);
    }

    @ParameterizedTest(name = "size={0}")
    @ValueSource(strings = {"0", "101", "-5"})
    void shouldRejectSizeWhenItIsOutsideOneToHundred(String value) {
      // given
      Map<String, List<String>> parameters = parameters("size", value);

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("size", "must be between 1 and 100"));
    }

    @ParameterizedTest(name = "size={0}")
    @ValueSource(strings = {"abc", "1.5", "2147483648", "+5", "\u0661\u0662"})
    void shouldRejectSizeWhenValueIsNotWholeNumber(String value) {
      // given
      Map<String, List<String>> parameters = parameters("size", value);

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations).containsExactly(new FieldViolation("size", "must be a whole number"));
    }
  }

  @Nested
  class BadShape {

    @Test
    void shouldRejectParameterWhenItIsRepeated() {
      // given
      Map<String, List<String>> parameters = new LinkedHashMap<>();
      parameters.put("page", List.of("1", "2"));

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations).containsExactly(new FieldViolation("page", "must be given only once"));
    }

    @Test
    void shouldRejectParameterWhenItIsRepeatedWithBlankValues() {
      // given
      Map<String, List<String>> parameters = new LinkedHashMap<>();
      parameters.put("size", List.of("", ""));

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations).containsExactly(new FieldViolation("size", "must be given only once"));
    }

    @Test
    void shouldRejectParameterWhenNameIsUnknown() {
      // given
      Map<String, List<String>> parameters = parameters("pgae", "2");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("pgae", "is not a known parameter"));
    }

    @Test
    void shouldReportUnknownParameterOnceWhenItHasSeveralValues() {
      // given
      Map<String, List<String>> parameters = new LinkedHashMap<>();
      parameters.put("colour", List.of("a", "b"));

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("colour", "is not a known parameter"));
    }
  }

  @Nested
  class SeveralProblems {

    @Test
    void shouldReportPageThenSizeThenUnknownNamesInIterationOrderWhenEverythingIsWrong() {
      // given
      Map<String, List<String>> parameters = new LinkedHashMap<>();
      parameters.put("zebra", List.of("1"));
      parameters.put("size", List.of("500"));
      parameters.put("apple", List.of("1"));
      parameters.put("page", List.of("-3"));

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(
              new FieldViolation("page", "must be 0 or more"),
              new FieldViolation("size", "must be between 1 and 100"),
              new FieldViolation("zebra", "is not a known parameter"),
              new FieldViolation("apple", "is not a known parameter"));
    }

    @Test
    void shouldNotRepeatRawValueWhenValuesAreNotNumbers() {
      // given
      Map<String, List<String>> parameters =
          parameters("page", "secret-page", "size", "secret-size", "other", "secret-other");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations).hasSize(3);
      assertThat(violations)
          .extracting(FieldViolation::message)
          .noneMatch(message -> message.contains("secret"));
    }

    @Test
    void shouldNotContainTheRawValueWhenNumbersAreOutOfRange() {
      // given
      Map<String, List<String>> parameters = parameters("page", "-98765", "size", "98765");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .extracting(FieldViolation::message)
          .noneMatch(message -> message.contains("98765"));
    }
  }

  @Nested
  class Searching {

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
      "name,alien",
      "minYear,2000",
      "maxYear,2000",
      "genre,drama",
      "viewerAge,12",
      "minRating,3",
      "maxRating,3",
      "director,spielberg",
      "actor,ford",
      "synopsis,missing"
    })
    void shouldKnowSearchParameterWhenItIsGiven(String name, String value) {
      // given
      Map<String, List<String>> parameters = parameters(name, value);

      // when / then
      assertThatCode(() -> MovieQuery.parse(parameters)).doesNotThrowAnyException();
    }

    @Test
    void shouldBuildCriteriaWhenSearchParameterIsGiven() {
      // given
      Map<String, List<String>> parameters = parameters("actor", "Harrison Ford");

      // when
      MovieQuery query = MovieQuery.parse(parameters);

      // then
      assertThat(query.criteria()).isEqualTo(new ActorNamed(List.of("harrison", "ford")));
      assertThat(query.paging()).isEqualTo(new MoviePageRequest(0, 20));
    }

    @Test
    void shouldUseAllCriteriaWhenOnlyPagingParametersAreGiven() {
      // given
      Map<String, List<String>> parameters = parameters("page", "1", "size", "5");

      // when
      MovieQuery query = MovieQuery.parse(parameters);

      // then
      assertThat(query.criteria()).isEqualTo(new All());
    }

    @Test
    void shouldReadSearchAndPagingTogetherWhenBothAreGiven() {
      // given
      Map<String, List<String>> parameters = parameters("page", "2", "size", "5", "name", "alien");

      // when
      MovieQuery query = MovieQuery.parse(parameters);

      // then
      assertThat(query.criteria()).isEqualTo(new NameContains("alien"));
      assertThat(query.paging()).isEqualTo(new MoviePageRequest(2, 5));
    }

    @Test
    void shouldRejectSearchParameterWhenItIsRepeated() {
      // given
      Map<String, List<String>> parameters = new LinkedHashMap<>();
      parameters.put("genre", List.of("a", "b"));

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("genre", "must be given only once"));
    }

    @Test
    void shouldRejectSearchParameterWhenItIsOutOfRange() {
      // given
      Map<String, List<String>> parameters = parameters("viewerAge", "121");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .containsExactly(new FieldViolation("viewerAge", "must be between 0 and 120"));
    }

    @Test
    void shouldReportPageSizeSearchProblemsThenUnknownNamesInOrderWhenEverythingIsWrong() {
      // given
      Map<String, List<String>> parameters = new LinkedHashMap<>();
      parameters.put("zebra", List.of("1"));
      parameters.put("actor", List.of("ford"));
      parameters.put("size", List.of("500"));
      parameters.put("genre", List.of("drama"));
      parameters.put("page", List.of("-3"));

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      String combined = "must not be combined with another search field; received: genre, actor";
      assertThat(violations)
          .containsExactly(
              new FieldViolation("page", "must be 0 or more"),
              new FieldViolation("size", "must be between 1 and 100"),
              new FieldViolation("genre", combined),
              new FieldViolation("actor", combined),
              new FieldViolation("zebra", "is not a known parameter"));
    }

    @Test
    void shouldKeepPagingProblemWhenTwoSearchFieldsAreAlsoGiven() {
      // given
      Map<String, List<String>> parameters = parameters("director", "x", "name", "y", "size", "0");

      // when
      List<FieldViolation> violations = violationsOf(parameters);

      // then
      assertThat(violations)
          .extracting(FieldViolation::field)
          .containsExactly("size", "name", "director");
    }
  }
}

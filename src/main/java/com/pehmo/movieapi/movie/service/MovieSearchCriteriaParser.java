package com.pehmo.movieapi.movie.service;

import static com.pehmo.movieapi.movie.service.MovieSearchCriteria.ACTOR;
import static com.pehmo.movieapi.movie.service.MovieSearchCriteria.DIRECTOR;
import static com.pehmo.movieapi.movie.service.MovieSearchCriteria.GENRE;
import static com.pehmo.movieapi.movie.service.MovieSearchCriteria.MAX_RATING;
import static com.pehmo.movieapi.movie.service.MovieSearchCriteria.MAX_YEAR;
import static com.pehmo.movieapi.movie.service.MovieSearchCriteria.MIN_RATING;
import static com.pehmo.movieapi.movie.service.MovieSearchCriteria.MIN_YEAR;
import static com.pehmo.movieapi.movie.service.MovieSearchCriteria.NAME;
import static com.pehmo.movieapi.movie.service.MovieSearchCriteria.SYNOPSIS;
import static com.pehmo.movieapi.movie.service.MovieSearchCriteria.VIEWER_AGE;

import com.pehmo.movieapi.movie.service.MovieSearchCriteria.ActorNamed;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.All;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.DirectorNamed;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.Genre;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.NameContains;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.RatingRange;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.SynopsisContains;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.ViewerAge;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.YearRange;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Turns the raw search parameters into {@link MovieSearchCriteria}. Package-private so {@link
 * MovieQuery} can collect these violations together with its own and report all of them at once.
 */
final class MovieSearchCriteriaParser {

  /**
   * The search fields in contract order, each with its parameters: the two bounds of a range are
   * one field, so sending both is not a combination. The single source of {@link
   * MovieSearchCriteria#PARAMETERS}: a new filter is added here.
   */
  static final List<List<String>> FIELDS =
      List.of(
          List.of(NAME),
          List.of(MIN_YEAR, MAX_YEAR),
          List.of(GENRE),
          List.of(VIEWER_AGE),
          List.of(MIN_RATING, MAX_RATING),
          List.of(DIRECTOR),
          List.of(ACTOR),
          List.of(SYNOPSIS));

  private MovieSearchCriteriaParser() {}

  /**
   * Returns the criteria, or null when a problem was added to {@code violations}. The rules are
   * described on {@link MovieSearchCriteria#of}.
   */
  static MovieSearchCriteria collect(
      Map<String, List<String>> parameters, List<FieldViolation> violations) {
    int violationsBefore = violations.size();
    String name = QueryParameterValues.singleValue(parameters, NAME, violations);
    Integer minYear = QueryParameterValues.wholeNumber(parameters, MIN_YEAR, violations);
    Integer maxYear = QueryParameterValues.wholeNumber(parameters, MAX_YEAR, violations);
    String genre = QueryParameterValues.singleValue(parameters, GENRE, violations);
    Integer viewerAge =
        numberBetween(parameters, VIEWER_AGE, ViewerAge.YOUNGEST, ViewerAge.OLDEST, violations);
    Integer minRating =
        numberBetween(parameters, MIN_RATING, RatingRange.LOWEST, RatingRange.HIGHEST, violations);
    Integer maxRating =
        numberBetween(parameters, MAX_RATING, RatingRange.LOWEST, RatingRange.HIGHEST, violations);
    String director = QueryParameterValues.singleValue(parameters, DIRECTOR, violations);
    String actor = QueryParameterValues.singleValue(parameters, ACTOR, violations);
    String synopsis = QueryParameterValues.singleValue(parameters, SYNOPSIS, violations);
    requireOrderedBounds(minYear, maxYear, MIN_YEAR, MAX_YEAR, violations);
    requireOrderedBounds(minRating, maxRating, MIN_RATING, MAX_RATING, violations);
    requireAtMostOneField(parameters, violations);
    if (violations.size() > violationsBefore) {
      return null;
    }
    // At most one field is left: the one-field rule passed.
    if (name != null) {
      return new NameContains(name);
    }
    if (minYear != null || maxYear != null) {
      return new YearRange(
          Objects.requireNonNullElse(minYear, Integer.MIN_VALUE),
          Objects.requireNonNullElse(maxYear, Integer.MAX_VALUE));
    }
    if (genre != null) {
      return new Genre(genre);
    }
    if (viewerAge != null) {
      return new ViewerAge(viewerAge);
    }
    if (minRating != null || maxRating != null) {
      return new RatingRange(
          Objects.requireNonNullElse(minRating, RatingRange.LOWEST),
          Objects.requireNonNullElse(maxRating, RatingRange.HIGHEST));
    }
    if (director != null) {
      return new DirectorNamed(wordsOf(director));
    }
    if (actor != null) {
      return new ActorNamed(wordsOf(actor));
    }
    if (synopsis != null) {
      return new SynopsisContains(wordsOf(synopsis));
    }
    return new All();
  }

  /** Returns the whole number when it is within the bounds, else null with a violation. */
  private static Integer numberBetween(
      Map<String, List<String>> parameters,
      String name,
      int lowest,
      int highest,
      List<FieldViolation> violations) {
    Integer number = QueryParameterValues.wholeNumber(parameters, name, violations);
    if (number != null && (number < lowest || number > highest)) {
      violations.add(new FieldViolation(name, "must be between " + lowest + " and " + highest));
      return null;
    }
    return number;
  }

  /** Checked only when both bounds are usable, so one mistake is not reported twice. */
  private static void requireOrderedBounds(
      Integer min, Integer max, String minName, String maxName, List<FieldViolation> violations) {
    if (min != null && max != null && min > max) {
      violations.add(new FieldViolation(minName, "must not be greater than " + maxName));
    }
  }

  /**
   * Rejects a combination of search fields, naming every parameter received. A parameter counts
   * when it has any non-blank value, even an unusable one, so the client sees every problem at once
   * instead of one per request.
   */
  private static void requireAtMostOneField(
      Map<String, List<String>> parameters, List<FieldViolation> violations) {
    List<List<String>> receivedFields =
        FIELDS.stream()
            .map(
                field ->
                    field.stream()
                        .filter(name -> QueryParameterValues.isGiven(parameters, name))
                        .toList())
            .filter(received -> !received.isEmpty())
            .toList();
    if (receivedFields.size() < 2) {
      return;
    }
    String received =
        receivedFields.stream()
            .map(field -> String.join(" + ", field))
            .collect(Collectors.joining(", "));
    String message = "must not be combined with another search field; received: " + received;
    for (List<String> field : receivedFields) {
      for (String name : field) {
        violations.add(new FieldViolation(name, message));
      }
    }
  }

  /**
   * Splits on single spaces: {@link TextNormalization#normalize} has already stripped the ends and
   * collapsed every whitespace run to one space.
   */
  private static List<String> wordsOf(String text) {
    return List.of(TextNormalization.normalize(text).split(" "));
  }
}

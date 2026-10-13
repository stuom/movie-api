package com.pehmo.movieapi.movie.service;

import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * The only place that holds the movie validation rules from the domain table in AGENTS.md
 * (ADR-0009). Both the API and the seed loader go through it, so they cannot disagree.
 *
 * <p>It needs a {@link Clock} because the latest allowed year is "current year + 5"; the year is
 * taken in the clock's zone.
 *
 * <p>The limits are public only so the OpenAPI annotations on the web DTOs can show them without a
 * second copy of the numbers; they are enforced here and nowhere else.
 */
@Component
public final class MovieRules {

  public static final int MAX_NAME_LENGTH = 200;
  public static final int FIRST_FILM_YEAR = 1888;
  public static final int YEARS_AHEAD_ALLOWED = 5;
  public static final int MIN_GENRES = 1;
  public static final int MAX_GENRES = 5;
  public static final int MAX_GENRE_LENGTH = 50;
  public static final int MIN_AGE_LIMIT = 0;
  public static final int MAX_AGE_LIMIT = 18;
  public static final int MIN_RATING = 1;
  public static final int MAX_RATING = 5;
  public static final int MAX_PERSON_NAME_LENGTH = 100;
  public static final int MAX_ACTORS = 30;
  public static final int MAX_SYNOPSIS_LENGTH = 2000;

  /** The one wording for an absent value, whether null or blank. */
  private static final String IS_REQUIRED = "is required";

  private final Clock clock;

  /** Creates the rules using {@code clock} for the current year. */
  public MovieRules(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Trims all text, checks every rule and returns the valid movie with its derived id.
   *
   * <p>A blank synopsis becomes null and missing actors become an empty list. Lengths are counted
   * in UTF-16 chars after trimming.
   *
   * @throws InvalidMovieException listing every violation, in field declaration order
   */
  public Movie toValidMovie(NewMovie newMovie) {
    List<FieldViolation> violations = new ArrayList<>();
    String name = requiredText("name", newMovie.name(), MAX_NAME_LENGTH, violations);
    int latestYear = Year.now(clock).getValue() + YEARS_AHEAD_ALLOWED;
    checkRange("year", newMovie.year(), FIRST_FILM_YEAR, latestYear, violations);
    List<String> genres = genres(newMovie.genres(), violations);
    checkRange("ageLimit", newMovie.ageLimit(), MIN_AGE_LIMIT, MAX_AGE_LIMIT, violations);
    checkRange("rating", newMovie.rating(), MIN_RATING, MAX_RATING, violations);
    Person director =
        newMovie.director() == null ? null : person("director", newMovie.director(), violations);
    List<Person> actors = actors(newMovie.actors(), violations);
    String synopsis =
        optionalText("synopsis", newMovie.synopsis(), MAX_SYNOPSIS_LENGTH, violations);
    if (!violations.isEmpty()) {
      throw new InvalidMovieException(violations);
    }
    int year = newMovie.year();
    return new Movie(
        MovieId.derivedFrom(name, year),
        name,
        year,
        genres,
        newMovie.ageLimit(),
        newMovie.rating(),
        director,
        actors,
        synopsis);
  }

  /**
   * Returns the trimmed text, or null after recording a violation when it is missing or blank; also
   * records a violation when it is too long.
   */
  private static String requiredText(
      String field, String text, int maxLength, List<FieldViolation> violations) {
    if (text == null || text.isBlank()) {
      violations.add(new FieldViolation(field, IS_REQUIRED));
      return null;
    }
    String trimmed = TextNormalization.trim(text);
    if (trimmed.length() > maxLength) {
      violations.add(new FieldViolation(field, "must be at most " + maxLength + " characters"));
    }
    return trimmed;
  }

  /** Records a violation when the number is missing or outside {@code min..max} (inclusive). */
  private static void checkRange(
      String field, Integer value, int min, int max, List<FieldViolation> violations) {
    if (value == null) {
      violations.add(new FieldViolation(field, IS_REQUIRED));
    } else if (value < min || value > max) {
      violations.add(new FieldViolation(field, "must be between " + min + " and " + max));
    }
  }

  /**
   * Returns the trimmed genres. When the count is wrong, entries are not checked one by one, so an
   * oversized body cannot produce an unbounded list of violations.
   */
  private static List<String> genres(List<String> genres, List<FieldViolation> violations) {
    if (genres == null) {
      violations.add(new FieldViolation("genres", IS_REQUIRED));
      return List.of();
    }
    if (genres.size() < MIN_GENRES || genres.size() > MAX_GENRES) {
      violations.add(
          new FieldViolation(
              "genres", "must have " + MIN_GENRES + " to " + MAX_GENRES + " entries"));
      return List.of();
    }
    List<String> trimmed = new ArrayList<>();
    for (int i = 0; i < genres.size(); i++) {
      trimmed.add(requiredText("genres[" + i + "]", genres.get(i), MAX_GENRE_LENGTH, violations));
    }
    return trimmed;
  }

  /**
   * Returns the actors with trimmed names, empty when there are none. Like {@link #genres}, entries
   * are not checked one by one when there are too many.
   */
  private static List<Person> actors(
      List<NewMovie.PersonInput> actors, List<FieldViolation> violations) {
    if (actors == null) {
      return List.of();
    }
    if (actors.size() > MAX_ACTORS) {
      violations.add(new FieldViolation("actors", "must have at most " + MAX_ACTORS + " entries"));
      return List.of();
    }
    List<Person> people = new ArrayList<>();
    for (int i = 0; i < actors.size(); i++) {
      String field = "actors[" + i + "]";
      NewMovie.PersonInput actor = actors.get(i);
      if (actor == null) {
        violations.add(new FieldViolation(field, IS_REQUIRED));
      } else {
        people.add(person(field, actor, violations));
      }
    }
    return people;
  }

  /** Returns the person with trimmed names, or null when a name breaks a rule. */
  private static Person person(
      String field, NewMovie.PersonInput input, List<FieldViolation> violations) {
    int before = violations.size();
    String firstName =
        requiredText(field + ".firstName", input.firstName(), MAX_PERSON_NAME_LENGTH, violations);
    String lastName =
        requiredText(field + ".lastName", input.lastName(), MAX_PERSON_NAME_LENGTH, violations);
    return violations.size() > before ? null : new Person(firstName, lastName);
  }

  /** Returns the trimmed text, or null when it is missing or blank (blank means not provided). */
  private static String optionalText(
      String field, String text, int maxLength, List<FieldViolation> violations) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String trimmed = TextNormalization.trim(text);
    if (trimmed.length() > maxLength) {
      violations.add(new FieldViolation(field, "must be at most " + maxLength + " characters"));
    }
    return trimmed;
  }
}

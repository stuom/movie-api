package com.pehmo.movieapi.movie.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a list request searches for: exactly one search field, or {@link All} for none (ADR-0007).
 *
 * <p>Sealed on purpose: {@link MovieSearch} handles the records in an exhaustive {@code switch}
 * without a {@code default}, so a new search field does not compile until it is matched. The
 * records are valid by construction and hold their text normalized ({@link
 * TextNormalization#normalize}); request values are checked by {@link #of}, which reports them to
 * the client, so an invalid record is a bug.
 */
public sealed interface MovieSearchCriteria {

  /** Query parameter: the movie name contains the text. */
  String NAME = "name";

  /** Query parameter: lowest release year, inclusive. */
  String MIN_YEAR = "minYear";

  /** Query parameter: highest release year, inclusive. */
  String MAX_YEAR = "maxYear";

  /** Query parameter: one of the genres equals the text. */
  String GENRE = "genre";

  /** Query parameter: age of the viewer; movies with an age limit up to it match. */
  String VIEWER_AGE = "viewerAge";

  /** Query parameter: lowest rating, inclusive. */
  String MIN_RATING = "minRating";

  /** Query parameter: highest rating, inclusive. */
  String MAX_RATING = "maxRating";

  /** Query parameter: every word is in the director's first or last name. */
  String DIRECTOR = "director";

  /** Query parameter: every word is in the first or last name of one actor. */
  String ACTOR = "actor";

  /** Query parameter: every word is in the synopsis. */
  String SYNOPSIS = "synopsis";

  /**
   * Every search parameter, in the order of the contract table and of the violations. Derived from
   * the parser's fields, so a parameter cannot be known without counting for the one-field rule.
   */
  List<String> PARAMETERS =
      MovieSearchCriteriaParser.FIELDS.stream().flatMap(List::stream).toList();

  /**
   * Reads the criteria from the search parameters of a request: each name with all of its values,
   * as received. Values are trimmed, and a blank value counts as not given. Parameters that are not
   * search parameters are ignored; rejecting them is {@link MovieQuery}'s job.
   *
   * @return {@link All} when no search field is given
   * @throws InvalidQueryException naming every search parameter that is repeated, not a whole
   *     number or out of range, a lower bound above its upper bound, and every search parameter
   *     received when more than one search field is given
   */
  static MovieSearchCriteria of(Map<String, List<String>> searchParameters) {
    List<FieldViolation> violations = new ArrayList<>();
    MovieSearchCriteria criteria = MovieSearchCriteriaParser.collect(searchParameters, violations);
    if (!violations.isEmpty()) {
      throw new InvalidQueryException(violations);
    }
    return criteria;
  }

  /** No search field: every movie matches. */
  record All() implements MovieSearchCriteria {}

  /**
   * The normalized movie name contains the text.
   *
   * @param text the normalized, non-blank text
   */
  record NameContains(String text) implements MovieSearchCriteria {

    /** Normalizes the text and rejects blank text, which means "not given". */
    public NameContains {
      text = nonBlankNormalized(text);
    }
  }

  /**
   * The release year is within the bounds, both inclusive. A bound the client left out is the
   * lowest or highest int, so matching needs no null checks.
   *
   * @param min the lowest year
   * @param max the highest year, not below {@code min}
   */
  record YearRange(int min, int max) implements MovieSearchCriteria {

    /** Rejects inverted bounds. */
    public YearRange {
      if (min > max) {
        throw new IllegalArgumentException("min must not be above max");
      }
    }
  }

  /**
   * One of the movie's normalized genres equals the text.
   *
   * @param text the normalized, non-blank genre
   */
  record Genre(String text) implements MovieSearchCriteria {

    /** Normalizes the text and rejects blank text, which means "not given". */
    public Genre {
      text = nonBlankNormalized(text);
    }
  }

  /**
   * The movie's age limit is at most the viewer's age.
   *
   * @param age the viewer's age, {@link #YOUNGEST} to {@link #OLDEST}
   */
  record ViewerAge(int age) implements MovieSearchCriteria {

    /** The lowest viewer age a client may send. */
    public static final int YOUNGEST = 0;

    /** The highest viewer age a client may send; a sanity bound, not a rule about movies. */
    public static final int OLDEST = 120;

    /** Rejects ages outside {@link #YOUNGEST} to {@link #OLDEST}. */
    public ViewerAge {
      if (age < YOUNGEST || age > OLDEST) {
        throw new IllegalArgumentException("age must be between 0 and 120");
      }
    }
  }

  /**
   * The rating is within the bounds, both inclusive. A bound the client left out is {@link #LOWEST}
   * or {@link #HIGHEST}.
   *
   * @param min the lowest rating
   * @param max the highest rating, not below {@code min}
   */
  record RatingRange(int min, int max) implements MovieSearchCriteria {

    /** The lowest rating a movie can have. */
    public static final int LOWEST = 1;

    /** The highest rating a movie can have. */
    public static final int HIGHEST = 5;

    /** Rejects bounds outside {@link #LOWEST} to {@link #HIGHEST} and inverted bounds. */
    public RatingRange {
      if (min < LOWEST || max > HIGHEST || min > max) {
        throw new IllegalArgumentException("rating bounds must be ordered and between 1 and 5");
      }
    }
  }

  /**
   * The movie has a director, and every word is in that person's normalized first or last name.
   *
   * @param words the normalized words, without duplicates, at least one
   */
  record DirectorNamed(List<String> words) implements MovieSearchCriteria {

    /** Normalizes the words, drops duplicates and freezes the list. */
    public DirectorNamed {
      words = List.copyOf(distinctNormalizedWords(words));
    }
  }

  /**
   * Some single actor has every word in their normalized first or last name. The words are never
   * pooled across actors: "ryan wright" does not match a cast of Ryan Gosling and Robin Wright.
   *
   * @param words the normalized words, without duplicates, at least one
   */
  record ActorNamed(List<String> words) implements MovieSearchCriteria {

    /** Normalizes the words, drops duplicates and freezes the list. */
    public ActorNamed {
      words = List.copyOf(distinctNormalizedWords(words));
    }
  }

  /**
   * The movie has a synopsis, and every word is in its normalized text, in any order.
   *
   * @param words the normalized words, without duplicates, at least one
   */
  record SynopsisContains(List<String> words) implements MovieSearchCriteria {

    /** Normalizes the words, drops duplicates and freezes the list. */
    public SynopsisContains {
      words = List.copyOf(distinctNormalizedWords(words));
    }
  }

  private static String nonBlankNormalized(String text) {
    String normalized = TextNormalization.normalize(text);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("text must not be blank");
    }
    return normalized;
  }

  /**
   * Duplicates are dropped so each query has one canonical form, and so a long, repetitive query
   * cannot multiply the matching work.
   */
  private static List<String> distinctNormalizedWords(List<String> words) {
    Objects.requireNonNull(words, "words");
    LinkedHashSet<String> distinct = new LinkedHashSet<>();
    for (String word : words) {
      String normalized = nonBlankNormalized(word);
      if (normalized.contains(" ")) {
        throw new IllegalArgumentException("a word must not contain a space");
      }
      distinct.add(normalized);
    }
    if (distinct.isEmpty()) {
      throw new IllegalArgumentException("words must not be empty");
    }
    return List.copyOf(distinct);
  }
}

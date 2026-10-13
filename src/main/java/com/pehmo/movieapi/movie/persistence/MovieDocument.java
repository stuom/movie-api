package com.pehmo.movieapi.movie.persistence;

import com.pehmo.movieapi.movie.service.Movie;
import com.pehmo.movieapi.movie.service.Person;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a {@link Movie} to and from the field map stored in a Firestore document. It is a plain
 * mapper, not a Firestore object: it uses no Firestore types, so the whole storage format is unit
 * tested without a database (ADR-0010).
 *
 * <p>The id is not a field: it is the document id. Optional parts that are unknown (director,
 * synopsis) are left out of the map instead of being stored as null. Reading is lenient about those
 * optional parts but strict about everything else, so a corrupt document fails loudly instead of
 * turning into a wrong movie.
 */
public final class MovieDocument {

  /**
   * Field holding the server time of the create, for diagnostics only. The repository writes it,
   * because the server timestamp is a Firestore type; reading ignores it.
   */
  static final String CREATED_AT = "createdAt";

  private static final String NAME = "name";
  private static final String YEAR = "year";
  private static final String GENRES = "genres";
  private static final String AGE_LIMIT = "ageLimit";
  private static final String RATING = "rating";
  private static final String DIRECTOR = "director";
  private static final String ACTORS = "actors";
  private static final String SYNOPSIS = "synopsis";
  private static final String FIRST_NAME = "firstName";
  private static final String LAST_NAME = "lastName";

  private MovieDocument() {}

  /** Returns the fields to store for this movie, without the id and without {@code createdAt}. */
  public static Map<String, Object> fieldsOf(Movie movie) {
    Map<String, Object> fields = new HashMap<>();
    fields.put(NAME, movie.name());
    fields.put(YEAR, movie.year());
    fields.put(GENRES, movie.genres());
    fields.put(AGE_LIMIT, movie.ageLimit());
    fields.put(RATING, movie.rating());
    if (movie.director() != null) {
      fields.put(DIRECTOR, fieldsOf(movie.director()));
    }
    fields.put(ACTORS, movie.actors().stream().map(MovieDocument::fieldsOf).toList());
    if (movie.synopsis() != null) {
      fields.put(SYNOPSIS, movie.synopsis());
    }
    return fields;
  }

  /**
   * Rebuilds a movie from a stored document. Unknown fields such as {@code createdAt} are ignored.
   *
   * @param id the document id, which is the movie id
   * @param fields the document fields as Firestore returns them (whole numbers arrive as {@code
   *     Long})
   * @throws IllegalStateException if a required field is missing or has the wrong type; the message
   *     names the field path and the document id
   */
  public static Movie toMovie(String id, Map<String, Object> fields) {
    Reader document = new Reader(id);
    return new Movie(
        id,
        document.text(fields, NAME),
        document.integer(fields, YEAR),
        document.texts(fields, GENRES),
        document.integer(fields, AGE_LIMIT),
        document.integer(fields, RATING),
        document.optionalPerson(fields, DIRECTOR),
        document.persons(fields, ACTORS),
        document.optionalText(fields, SYNOPSIS));
  }

  private static Map<String, Object> fieldsOf(Person person) {
    return Map.of(FIRST_NAME, person.firstName(), LAST_NAME, person.lastName());
  }

  /** Reads typed values from one document, naming the document id and field path on failure. */
  private record Reader(String documentId) {

    String text(Map<?, ?> fields, String path) {
      return textValue(fields.get(path), path);
    }

    String optionalText(Map<?, ?> fields, String path) {
      Object value = fields.get(path);
      return value == null ? null : textValue(value, path);
    }

    int integer(Map<?, ?> fields, String path) {
      Object value = fields.get(path);
      // Firestore returns whole numbers as Long. A Double, even 1986.0, means the document was
      // written by something else, so it is rejected rather than converted (ADR-0010).
      if (value instanceof Long || value instanceof Integer) {
        long number = ((Number) value).longValue();
        if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
          return (int) number;
        }
      }
      throw invalid(path, "a whole number", value);
    }

    List<String> texts(Map<?, ?> fields, String path) {
      List<String> result = new ArrayList<>();
      List<?> values = list(fields.get(path), path);
      for (int i = 0; i < values.size(); i++) {
        result.add(textValue(values.get(i), path + "[" + i + "]"));
      }
      return result;
    }

    Person optionalPerson(Map<?, ?> fields, String path) {
      Object value = fields.get(path);
      return value == null ? null : person(value, path);
    }

    List<Person> persons(Map<?, ?> fields, String path) {
      Object value = fields.get(path);
      if (value == null) {
        return List.of();
      }
      List<Person> result = new ArrayList<>();
      List<?> values = list(value, path);
      for (int i = 0; i < values.size(); i++) {
        result.add(person(values.get(i), path + "[" + i + "]"));
      }
      return result;
    }

    private Person person(Object value, String path) {
      if (!(value instanceof Map<?, ?> person)) {
        throw invalid(path, "a map", value);
      }
      return new Person(
          textValue(person.get(FIRST_NAME), path + "." + FIRST_NAME),
          textValue(person.get(LAST_NAME), path + "." + LAST_NAME));
    }

    private List<?> list(Object value, String path) {
      if (value instanceof List<?> list) {
        return list;
      }
      throw invalid(path, "a list", value);
    }

    private String textValue(Object value, String path) {
      if (value instanceof String text) {
        return text;
      }
      throw invalid(path, "a string", value);
    }

    private IllegalStateException invalid(String path, String expected, Object actual) {
      String found = actual == null ? "nothing" : actual.getClass().getSimpleName();
      return new IllegalStateException(
          "Movie document %s: field '%s' must be %s but is %s"
              .formatted(documentId, path, expected, found));
    }
  }
}

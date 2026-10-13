package com.pehmo.movieapi.movie.persistence;

import static com.pehmo.movieapi.movie.service.MovieTestData.aMovie;
import static com.pehmo.movieapi.movie.service.MovieTestData.aPerson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pehmo.movieapi.movie.service.Movie;
import com.pehmo.movieapi.movie.service.Person;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MovieDocumentTest {

  private static final String ID = "movie-id-1";

  /** A complete document as Firestore returns it: whole numbers are Long. */
  private static Map<String, Object> aStoredDocument() {
    Map<String, Object> fields = new HashMap<>();
    fields.put("name", "Aliens");
    fields.put("year", 1986L);
    fields.put("genres", new ArrayList<>(List.of("Action", "Sci-fi")));
    fields.put("ageLimit", 16L);
    fields.put("rating", 5L);
    fields.put("director", new HashMap<>(Map.of("firstName", "James", "lastName", "Cameron")));
    fields.put(
        "actors",
        new ArrayList<>(
            List.of(
                new HashMap<>(Map.of("firstName", "Sigourney", "lastName", "Weaver")),
                new HashMap<>(Map.of("firstName", "Michael", "lastName", "Biehn")))));
    fields.put("synopsis", "Ellen Ripley is back.");
    return fields;
  }

  @Nested
  class FieldsOf {

    @Test
    void shouldWriteAllFieldsWhenMovieIsComplete() {
      // given
      Movie movie =
          aMovie()
              .withName("Aliens")
              .withYear(1986)
              .withGenres(List.of("Action", "Sci-fi"))
              .withAgeLimit(16)
              .withRating(5)
              .withDirector(aPerson("James", "Cameron"))
              .withActors(List.of(aPerson("Sigourney", "Weaver")))
              .withSynopsis("Ellen Ripley is back.")
              .buildMovie();

      // when
      Map<String, Object> fields = MovieDocument.fieldsOf(movie);

      // then
      assertThat(fields)
          .containsEntry("name", "Aliens")
          .containsEntry("year", 1986)
          .containsEntry("genres", List.of("Action", "Sci-fi"))
          .containsEntry("ageLimit", 16)
          .containsEntry("rating", 5)
          .containsEntry("synopsis", "Ellen Ripley is back.");
    }

    @Test
    void shouldWriteDirectorAsMapWhenDirectorIsKnown() {
      // given
      Movie movie = aMovie().withDirector(aPerson("James", "Cameron")).buildMovie();

      // when
      Map<String, Object> fields = MovieDocument.fieldsOf(movie);

      // then
      assertThat(fields.get("director"))
          .isEqualTo(Map.of("firstName", "James", "lastName", "Cameron"));
    }

    @Test
    void shouldWriteActorsAsListOfMapsWhenActorsAreKnown() {
      // given
      Movie movie =
          aMovie()
              .withActors(List.of(aPerson("Sigourney", "Weaver"), aPerson("Michael", "Biehn")))
              .buildMovie();

      // when
      Map<String, Object> fields = MovieDocument.fieldsOf(movie);

      // then
      assertThat(fields.get("actors"))
          .isEqualTo(
              List.of(
                  Map.of("firstName", "Sigourney", "lastName", "Weaver"),
                  Map.of("firstName", "Michael", "lastName", "Biehn")));
    }

    @Test
    void shouldOmitDirectorWhenUnknown() {
      // given
      Movie movie = aMovie().withDirector(null).buildMovie();

      // when
      Map<String, Object> fields = MovieDocument.fieldsOf(movie);

      // then
      assertThat(fields).doesNotContainKey("director");
    }

    @Test
    void shouldOmitSynopsisWhenThereIsNone() {
      // given
      Movie movie = aMovie().withSynopsis(null).buildMovie();

      // when
      Map<String, Object> fields = MovieDocument.fieldsOf(movie);

      // then
      assertThat(fields).doesNotContainKey("synopsis");
    }

    @Test
    void shouldWriteEmptyActorListWhenNoActorsAreKnown() {
      // given
      Movie movie = aMovie().withActors(List.of()).buildMovie();

      // when
      Map<String, Object> fields = MovieDocument.fieldsOf(movie);

      // then
      assertThat(fields).containsEntry("actors", List.of());
    }

    @Test
    void shouldNotWriteIdWhenWritingFields() {
      // given
      Movie movie = aMovie().buildMovie();

      // when
      Map<String, Object> fields = MovieDocument.fieldsOf(movie);

      // then
      assertThat(fields).doesNotContainKey("id");
    }

    @Test
    void shouldNotWriteCreatedAtWhenWritingFields() {
      // given
      Movie movie = aMovie().buildMovie();

      // when
      Map<String, Object> fields = MovieDocument.fieldsOf(movie);

      // then
      assertThat(fields).doesNotContainKey("createdAt");
    }
  }

  @Nested
  class ToMovie {

    @Test
    void shouldMapAllFieldsWhenDocumentIsComplete() {
      // given
      Map<String, Object> fields = aStoredDocument();

      // when
      Movie movie = MovieDocument.toMovie(ID, fields);

      // then
      assertThat(movie)
          .isEqualTo(
              new Movie(
                  ID,
                  "Aliens",
                  1986,
                  List.of("Action", "Sci-fi"),
                  16,
                  5,
                  new Person("James", "Cameron"),
                  List.of(new Person("Sigourney", "Weaver"), new Person("Michael", "Biehn")),
                  "Ellen Ripley is back."));
    }

    @Test
    void shouldTakeIdFromArgumentWhenDocumentHasNoIdField() {
      // given
      Map<String, Object> fields = aStoredDocument();

      // when
      Movie movie = MovieDocument.toMovie("some-other-id", fields);

      // then
      assertThat(movie.id()).isEqualTo("some-other-id");
    }

    @Test
    void shouldConvertLongNumbersToIntWhenFirestoreReturnsLongs() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("year", 2009L);
      fields.put("ageLimit", 12L);
      fields.put("rating", 3L);

      // when
      Movie movie = MovieDocument.toMovie(ID, fields);

      // then
      assertThat(movie.year()).isEqualTo(2009);
      assertThat(movie.ageLimit()).isEqualTo(12);
      assertThat(movie.rating()).isEqualTo(3);
    }

    @Test
    void shouldReturnNullDirectorWhenDirectorIsMissing() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.remove("director");

      // when
      Movie movie = MovieDocument.toMovie(ID, fields);

      // then
      assertThat(movie.director()).isNull();
    }

    @Test
    void shouldReturnNullDirectorWhenDirectorIsNull() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("director", null);

      // when
      Movie movie = MovieDocument.toMovie(ID, fields);

      // then
      assertThat(movie.director()).isNull();
    }

    @Test
    void shouldReturnEmptyActorsWhenActorsAreMissing() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.remove("actors");

      // when
      Movie movie = MovieDocument.toMovie(ID, fields);

      // then
      assertThat(movie.actors()).isEmpty();
    }

    @Test
    void shouldReturnNullSynopsisWhenSynopsisIsMissing() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.remove("synopsis");

      // when
      Movie movie = MovieDocument.toMovie(ID, fields);

      // then
      assertThat(movie.synopsis()).isNull();
    }

    @Test
    void shouldIgnoreCreatedAtAndUnknownFieldsWhenReading() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("createdAt", new java.util.Date(0));
      fields.put("somethingNew", 42L);

      // when
      Movie movie = MovieDocument.toMovie(ID, fields);

      // then
      assertThat(movie).isEqualTo(MovieDocument.toMovie(ID, aStoredDocument()));
      assertThat(movie.name()).isEqualTo("Aliens");
    }
  }

  @Nested
  class ToMovieFailures {

    private void assertFailsNaming(Map<String, Object> fields, String path) {
      assertThatThrownBy(() -> MovieDocument.toMovie(ID, fields))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(path)
          .hasMessageContaining(ID);
    }

    @Test
    void shouldFailWhenActorsAreNotListed() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("actors", "Sigourney Weaver");

      // when / then
      assertFailsNaming(fields, "actors");
    }

    @Test
    void shouldFailWhenOneActorIsNotMapped() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("actors", new ArrayList<>(List.of("Sigourney Weaver")));

      // when / then
      assertFailsNaming(fields, "actors[0]");
    }

    @Test
    void shouldFailWhenDirectorIsPlainText() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("director", "James Cameron");

      // when / then
      assertFailsNaming(fields, "director");
    }

    @Test
    void shouldFailWhenSynopsisIsNotText() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("synopsis", 12L);

      // when / then
      assertFailsNaming(fields, "synopsis");
    }

    @Test
    void shouldFailWhenGenresKeyIsMissing() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.remove("genres");

      // when / then
      assertFailsNaming(fields, "genres");
    }

    @Test
    void shouldFailWhenYearIsOutsideIntRange() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("year", 3_000_000_000L);

      // when / then
      assertFailsNaming(fields, "year");
    }

    @Test
    void shouldFailWhenYearIsWholeNumberDouble() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("year", 1986.0d);

      // when / then
      assertFailsNaming(fields, "year");
    }

    @Test
    void shouldFailNamingNameAndIdWhenNameIsMissing() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.remove("name");

      // when / then
      assertThatThrownBy(() -> MovieDocument.toMovie(ID, fields))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("name")
          .hasMessageContaining(ID);
    }

    @Test
    void shouldFailNamingYearAndIdWhenYearIsMissing() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.remove("year");

      // when / then
      assertThatThrownBy(() -> MovieDocument.toMovie(ID, fields))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("year")
          .hasMessageContaining(ID);
    }

    @Test
    void shouldFailNamingGenresAndIdWhenGenresAreNull() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("genres", null);

      // when / then
      assertThatThrownBy(() -> MovieDocument.toMovie(ID, fields))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("genres")
          .hasMessageContaining(ID);
    }

    @Test
    void shouldFailNamingGenrePathAndIdWhenOneGenreIsNotText() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("genres", new ArrayList<>(List.of("Action", 7L)));

      // when / then
      assertThatThrownBy(() -> MovieDocument.toMovie(ID, fields))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("genres[1]")
          .hasMessageContaining(ID);
    }

    @Test
    void shouldFailNamingActorPathAndIdWhenAnActorHasNoLastName() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put(
          "actors", new ArrayList<>(List.of(new HashMap<>(Map.of("firstName", "Sigourney")))));

      // when / then
      assertThatThrownBy(() -> MovieDocument.toMovie(ID, fields))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("actors[0].lastName")
          .hasMessageContaining(ID);
    }

    @Test
    void shouldFailNamingDirectorPathAndIdWhenDirectorFirstNameIsNotText() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("director", new HashMap<>(Map.of("firstName", 5L, "lastName", "Cameron")));

      // when / then
      assertThatThrownBy(() -> MovieDocument.toMovie(ID, fields))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("director.firstName")
          .hasMessageContaining(ID);
    }

    @Test
    void shouldFailNamingRatingAndIdWhenNumberIsFractional() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("rating", 4.5d);

      // when / then
      assertThatThrownBy(() -> MovieDocument.toMovie(ID, fields))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("rating")
          .hasMessageContaining(ID);
    }

    @Test
    void shouldFailNamingYearAndIdWhenYearIsText() {
      // given
      Map<String, Object> fields = aStoredDocument();
      fields.put("year", "1986");

      // when / then
      assertThatThrownBy(() -> MovieDocument.toMovie(ID, fields))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("year")
          .hasMessageContaining(ID);
    }
  }

  @Nested
  class RoundTrip {

    @Test
    void shouldRestoreSameMovieWhenAllOptionalFieldsArePresent() {
      // given
      Movie movie =
          aMovie()
              .withDirector(aPerson("Guillermo", "del Toro"))
              .withActors(List.of(aPerson("Sigourney", "Weaver"), aPerson("Michael", "Biehn")))
              .withSynopsis("Ellen Ripley is back.")
              .buildMovie();

      // when
      Movie restored = MovieDocument.toMovie(movie.id(), asFirestoreReturnsIt(fieldsOf(movie)));

      // then
      assertThat(restored).isEqualTo(movie);
    }

    @Test
    void shouldRestoreSameMovieWhenNoOptionalFieldsArePresent() {
      // given
      Movie movie =
          aMovie().withDirector(null).withActors(List.of()).withSynopsis(null).buildMovie();

      // when
      Movie restored = MovieDocument.toMovie(movie.id(), asFirestoreReturnsIt(fieldsOf(movie)));

      // then
      assertThat(restored).isEqualTo(movie);
    }

    @Test
    void shouldRestoreSameMovieWhenFieldsAreReadBackWithoutConversion() {
      // given
      Movie movie = aMovie().buildMovie();

      // when
      Movie restored = MovieDocument.toMovie(movie.id(), fieldsOf(movie));

      // then
      assertThat(restored).isEqualTo(movie);
    }

    private Map<String, Object> fieldsOf(Movie movie) {
      return MovieDocument.fieldsOf(movie);
    }

    /** Simulates the Firestore round trip, where every whole number comes back as Long. */
    private Map<String, Object> asFirestoreReturnsIt(Map<String, Object> written) {
      Map<String, Object> read = new HashMap<>(written);
      for (String key : List.of("year", "ageLimit", "rating")) {
        read.put(key, ((Number) written.get(key)).longValue());
      }
      return read;
    }
  }
}

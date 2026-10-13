package com.pehmo.movieapi.movie.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link Movie} is also built directly from stored data, bypassing {@link MovieRules}, so its
 * structural checks are tested on their own.
 */
class MovieTest {

  @Test
  void shouldRejectMovieWhenIdIsNull() {
    // given / when / then
    assertThatThrownBy(() -> movieWith(null, "Aliens", List.of("Action"), List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("id");
  }

  @Test
  void shouldRejectMovieWhenNameIsNull() {
    // given / when / then
    assertThatThrownBy(() -> movieWith("some-id", null, List.of("Action"), List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name");
  }

  @Test
  void shouldRejectMovieWhenGenresAreNull() {
    // given / when / then
    assertThatThrownBy(() -> movieWith("some-id", "Aliens", null, List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectMovieWhenActorsAreNull() {
    // given / when / then
    assertThatThrownBy(() -> movieWith("some-id", "Aliens", List.of("Action"), null))
        .isInstanceOf(NullPointerException.class);
  }

  private static Movie movieWith(String id, String name, List<String> genres, List<Person> actors) {
    return new Movie(id, name, 1986, genres, 16, 5, null, actors, null);
  }
}

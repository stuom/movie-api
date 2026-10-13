package com.pehmo.movieapi.movie.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PersonTest {

  @Test
  void shouldRejectPersonWhenFirstNameIsNull() {
    // given / when / then
    assertThatThrownBy(() -> new Person(null, "Cameron"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("firstName");
  }

  @Test
  void shouldRejectPersonWhenLastNameIsNull() {
    // given / when / then
    assertThatThrownBy(() -> new Person("James", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("lastName");
  }
}

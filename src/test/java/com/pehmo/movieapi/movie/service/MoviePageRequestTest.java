package com.pehmo.movieapi.movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoviePageRequestTest {

  @ParameterizedTest(name = "page {0}, size {1}")
  @CsvSource({"0, 1", "0, 100", "5, 20", "2147483647, 100"})
  void shouldAcceptPageAndSizeWhenWithinBounds(int page, int size) {
    // when
    MoviePageRequest request = new MoviePageRequest(page, size);

    // then
    assertThat(request.page()).isEqualTo(page);
    assertThat(request.size()).isEqualTo(size);
  }

  @ParameterizedTest(name = "page {0}, size {1}")
  @CsvSource({"-1, 20", "0, 0", "0, 101", "0, -1"})
  void shouldRejectRequestWhenPageOrSizeIsOutOfBounds(int page, int size) {
    // when / then
    assertThatThrownBy(() -> new MoviePageRequest(page, size))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

package com.pehmo.movieapi.movie.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MovieIdTest {

  @Test
  void shouldGiveSameIdWhenNameAndYearAreTheSame() {
    // given
    String name = "Aliens";

    // when
    String first = MovieId.derivedFrom(name, 1986);
    String second = MovieId.derivedFrom(name, 1986);

    // then
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldGiveSameIdWhenNameDiffersOnlyInCase() {
    // given
    int year = 1986;

    // when
    String lower = MovieId.derivedFrom("aliens", year);
    String upper = MovieId.derivedFrom("ALIENS", year);

    // then
    assertThat(upper).isEqualTo(lower);
  }

  @Test
  void shouldGiveSameIdWhenNameHasExtraSpaces() {
    // given
    int year = 1977;

    // when
    String plain = MovieId.derivedFrom("Star Wars", year);
    String spaced = MovieId.derivedFrom("  Star   Wars ", year);

    // then
    assertThat(spaced).isEqualTo(plain);
  }

  @Test
  void shouldGiveSameIdWhenAccentIsComposedOrDecomposed() {
    // given
    int year = 2001;

    // when
    String composed = MovieId.derivedFrom("Am\u00E9lie", year);
    String decomposed = MovieId.derivedFrom("Ame\u0301lie", year);

    // then
    assertThat(decomposed).isEqualTo(composed);
  }

  @Test
  void shouldGiveDifferentIdWhenYearDiffers() {
    // given
    String name = "Dune";

    // when
    String id1984 = MovieId.derivedFrom(name, 1984);
    String id2021 = MovieId.derivedFrom(name, 2021);

    // then
    assertThat(id1984).isNotEqualTo(id2021);
  }

  @Test
  void shouldGiveDifferentIdWhenNameDiffers() {
    // given
    int year = 1986;

    // when
    String aliens = MovieId.derivedFrom("Aliens", year);
    String platoon = MovieId.derivedFrom("Platoon", year);

    // then
    assertThat(aliens).isNotEqualTo(platoon);
  }

  @Test
  void shouldReturn32LowercaseHexCharactersWhenDerivingId() {
    // given
    String name = "Aliens";

    // when
    String id = MovieId.derivedFrom(name, 1986);

    // then
    assertThat(id).matches("[0-9a-f]{32}");
  }

  // Ids are persisted in the database, so the golden values below must never change: a change
  // would give every existing movie a different id and break the duplicate check.

  @Test
  void shouldMatchGoldenIdWhenNameIsAliens() {
    // given
    // printf 'aliens|1986' | sha256sum | cut -c1-32
    String expectedId = "53e87be920fdef6e6176fa0c355406d8";

    // when
    String id = MovieId.derivedFrom("Aliens", 1986);

    // then
    assertThat(id).isEqualTo(expectedId);
  }

  @Test
  void shouldMatchGoldenIdWhenNameHasAccent() {
    // given
    // printf 'am\xc3\xa9lie|2001' | sha256sum | cut -c1-32
    String expectedId = "40454ad510a87b6065b4b150928b2b65";

    // when
    String id = MovieId.derivedFrom("Am\u00E9lie", 2001);

    // then
    assertThat(id).isEqualTo(expectedId);
  }
}

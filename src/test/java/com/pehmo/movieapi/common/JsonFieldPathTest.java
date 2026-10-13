package com.pehmo.movieapi.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonFieldPathTest {

  private static final Object OWNER = new Object();

  @Test
  void shouldRenderFieldNameWhenPathHasOneField() {
    // given
    List<Reference> path = List.of(new Reference(OWNER, "name"));

    // when
    String rendered = JsonFieldPath.of(path);

    // then
    assertThat(rendered).isEqualTo("name");
  }

  @Test
  void shouldRenderIndexInBracketsWhenPathEndsInListEntry() {
    // given
    List<Reference> path = List.of(new Reference(OWNER, "genres"), new Reference(OWNER, 1));

    // when
    String rendered = JsonFieldPath.of(path);

    // then
    assertThat(rendered).isEqualTo("genres[1]");
  }

  @Test
  void shouldJoinWithDotWhenPathEntersNestedObject() {
    // given
    List<Reference> path =
        List.of(new Reference(OWNER, "director"), new Reference(OWNER, "firstName"));

    // when
    String rendered = JsonFieldPath.of(path);

    // then
    assertThat(rendered).isEqualTo("director.firstName");
  }

  @Test
  void shouldRenderIndexThenFieldWhenPathEntersListEntryObject() {
    // given
    List<Reference> path =
        List.of(
            new Reference(OWNER, "actors"),
            new Reference(OWNER, 2),
            new Reference(OWNER, "lastName"));

    // when
    String rendered = JsonFieldPath.of(path);

    // then
    assertThat(rendered).isEqualTo("actors[2].lastName");
  }
}

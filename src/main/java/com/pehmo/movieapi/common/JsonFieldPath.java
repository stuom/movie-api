package com.pehmo.movieapi.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;

/**
 * Renders the location of a JSON reading error in the same path format that {@code MovieRules} uses
 * ({@code actors[2].lastName}), so a client sees one format for every {@code 400}.
 */
final class JsonFieldPath {

  private JsonFieldPath() {}

  /** Returns the path, for example {@code genres[1]} or {@code director.firstName}. */
  static String of(List<JsonMappingException.Reference> path) {
    StringBuilder rendered = new StringBuilder();
    for (JsonMappingException.Reference step : path) {
      if (step.getFieldName() == null) {
        rendered.append('[').append(step.getIndex()).append(']');
      } else {
        if (!rendered.isEmpty()) {
          rendered.append('.');
        }
        rendered.append(step.getFieldName());
      }
    }
    return rendered.toString();
  }
}

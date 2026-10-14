package com.pehmo.movieapi.movie.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads single query parameter values the same way for paging and for search, so every parameter is
 * trimmed, repeated and checked as a number by the same rules. Messages never repeat a value,
 * because it is client text.
 */
final class QueryParameterValues {

  // ASCII digits only: Integer.parseInt alone would also accept "+5" and non-ASCII digits.
  private static final Pattern WHOLE_NUMBER = Pattern.compile("-?[0-9]+");

  private QueryParameterValues() {}

  /**
   * Returns the trimmed value of the parameter, or null when it is not given or blank. A parameter
   * given more than once is added to {@code violations} and returns null.
   */
  static String singleValue(
      Map<String, List<String>> parameters, String name, List<FieldViolation> violations) {
    List<String> values = parameters.getOrDefault(name, List.of());
    if (values.size() > 1) {
      violations.add(new FieldViolation(name, "must be given only once"));
      return null;
    }
    String value = values.isEmpty() ? null : TextNormalization.trim(values.get(0));
    return value == null || value.isEmpty() ? null : value;
  }

  /**
   * Returns the parameter as a whole number, or null when it is not given or is unusable. An
   * unusable value (repeated, not a whole number, beyond the int range) is added to {@code
   * violations}.
   */
  static Integer wholeNumber(
      Map<String, List<String>> parameters, String name, List<FieldViolation> violations) {
    String value = singleValue(parameters, name, violations);
    if (value == null) {
      return null;
    }
    if (!WHOLE_NUMBER.matcher(value).matches()) {
      violations.add(new FieldViolation(name, "must be a whole number"));
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException beyondIntRange) {
      violations.add(new FieldViolation(name, "must be a whole number"));
      return null;
    }
  }

  /**
   * Tells whether the parameter has a non-blank value, even a repeated or unusable one. The
   * one-field rule counts such a parameter as given, so a client learns about every problem at
   * once.
   */
  static boolean isGiven(Map<String, List<String>> parameters, String name) {
    return parameters.getOrDefault(name, List.of()).stream()
        .anyMatch(value -> value != null && !TextNormalization.trim(value).isEmpty());
  }
}

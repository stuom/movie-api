package com.pehmo.movieapi.movie.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A list request, read from its raw query parameters. Parsing lives here, not in the controller, so
 * every rule is plain Java with unit tests and every problem is reported at once.
 *
 * <p>Unknown parameters are rejected, like unknown fields in a request body (ADR-0012): a typo such
 * as {@code pgae=2} would otherwise silently return page 0. The search slices add their parameters
 * to the known ones and their criteria to this record.
 *
 * @param paging the page to return
 */
public record MovieQuery(MoviePageRequest paging) {

  private static final String PAGE = "page";
  private static final String SIZE = "size";
  private static final Set<String> KNOWN_PARAMETERS = Set.of(PAGE, SIZE);
  // ASCII digits only: Integer.parseInt alone would also accept "+5" and non-ASCII digits.
  private static final Pattern WHOLE_NUMBER = Pattern.compile("-?[0-9]+");

  /** Requires the paging. */
  public MovieQuery {
    Objects.requireNonNull(paging, "paging");
  }

  /**
   * Reads the query from the request parameters as received: each name with all of its values.
   * Values are trimmed, and a blank value counts as not given. Messages never repeat a value,
   * because it is client text.
   *
   * @throws InvalidQueryException naming every parameter that is out of range, not a whole number,
   *     repeated or unknown, in that order: {@code page}, {@code size}, then unknown names
   */
  public static MovieQuery parse(Map<String, List<String>> parameters) {
    List<FieldViolation> violations = new ArrayList<>();
    Integer page = wholeNumber(parameters, PAGE, violations);
    if (page != null && page < 0) {
      violations.add(new FieldViolation(PAGE, "must be 0 or more"));
    }
    Integer size = wholeNumber(parameters, SIZE, violations);
    if (size != null && (size < 1 || size > MoviePageRequest.MAX_SIZE)) {
      violations.add(
          new FieldViolation(SIZE, "must be between 1 and " + MoviePageRequest.MAX_SIZE));
    }
    for (String name : parameters.keySet()) {
      if (!KNOWN_PARAMETERS.contains(name)) {
        violations.add(new FieldViolation(name, "is not a known parameter"));
      }
    }
    if (!violations.isEmpty()) {
      throw new InvalidQueryException(violations);
    }
    return new MovieQuery(
        new MoviePageRequest(
            Objects.requireNonNullElse(page, MoviePageRequest.DEFAULT_PAGE),
            Objects.requireNonNullElse(size, MoviePageRequest.DEFAULT_SIZE)));
  }

  /**
   * Returns the parameter as a whole number, or null when it is not given or is unusable. An
   * unusable value is added to {@code violations}.
   */
  private static Integer wholeNumber(
      Map<String, List<String>> parameters, String name, List<FieldViolation> violations) {
    List<String> values = parameters.getOrDefault(name, List.of());
    if (values.size() > 1) {
      violations.add(new FieldViolation(name, "must be given only once"));
      return null;
    }
    String value = values.isEmpty() ? null : TextNormalization.trim(values.get(0));
    if (value == null || value.isEmpty()) {
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
}

package com.pehmo.movieapi.movie.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A list or search request, read from its raw query parameters. Parsing lives here, not in the
 * controller, so every rule is plain Java with unit tests and every problem is reported at once.
 *
 * <p>Unknown parameters are rejected, like unknown fields in a request body (ADR-0012): a typo such
 * as {@code pgae=2} would otherwise silently return page 0.
 *
 * @param criteria what to search for; {@link MovieSearchCriteria.All} lists every movie
 * @param paging the page to return
 */
public record MovieQuery(MovieSearchCriteria criteria, MoviePageRequest paging) {

  private static final String PAGE = "page";
  private static final String SIZE = "size";
  private static final Set<String> KNOWN_PARAMETERS = knownParameters();

  /** Requires the criteria and the paging. */
  public MovieQuery {
    Objects.requireNonNull(criteria, "criteria");
    Objects.requireNonNull(paging, "paging");
  }

  /**
   * Reads the query from the request parameters as received: each name with all of its values.
   * Values are trimmed, and a blank value counts as not given. Messages never repeat a value,
   * because it is client text.
   *
   * @throws InvalidQueryException naming every problem, in this order: {@code page}, {@code size},
   *     the search parameters (see {@link MovieSearchCriteria#of}), then unknown names
   */
  public static MovieQuery parse(Map<String, List<String>> parameters) {
    List<FieldViolation> violations = new ArrayList<>();
    Integer page = QueryParameterValues.wholeNumber(parameters, PAGE, violations);
    if (page != null && page < 0) {
      violations.add(new FieldViolation(PAGE, "must be 0 or more"));
    }
    Integer size = QueryParameterValues.wholeNumber(parameters, SIZE, violations);
    if (size != null && (size < 1 || size > MoviePageRequest.MAX_SIZE)) {
      violations.add(
          new FieldViolation(SIZE, "must be between 1 and " + MoviePageRequest.MAX_SIZE));
    }
    MovieSearchCriteria criteria = MovieSearchCriteriaParser.collect(parameters, violations);
    for (String name : parameters.keySet()) {
      if (!KNOWN_PARAMETERS.contains(name)) {
        violations.add(new FieldViolation(name, "is not a known parameter"));
      }
    }
    if (!violations.isEmpty()) {
      throw new InvalidQueryException(violations);
    }
    return new MovieQuery(
        criteria,
        new MoviePageRequest(
            Objects.requireNonNullElse(page, MoviePageRequest.DEFAULT_PAGE),
            Objects.requireNonNullElse(size, MoviePageRequest.DEFAULT_SIZE)));
  }

  private static Set<String> knownParameters() {
    Set<String> known = new HashSet<>(MovieSearchCriteria.PARAMETERS);
    known.add(PAGE);
    known.add(SIZE);
    return Set.copyOf(known);
  }
}

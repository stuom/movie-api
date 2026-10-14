package com.pehmo.movieapi.movie.service;

import java.io.Serial;
import java.util.List;

/**
 * Thrown when the query parameters of a list request are unusable: out of range, not a whole
 * number, repeated, unknown, a lower bound above its upper bound, or more than one search field. It
 * carries every problem, so the API can answer {@code 400} naming all of them at once (ADR-0004).
 */
public final class InvalidQueryException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  // An array, not a List: exceptions are Serializable and List is not a serializable type.
  private final FieldViolation[] violations;

  /** Creates the exception; {@code violations} must not be empty. */
  public InvalidQueryException(List<FieldViolation> violations) {
    // Fixed text: unknown parameter names are client text, and the message may end up in a log.
    super("Invalid query parameters");
    this.violations = violations.toArray(FieldViolation[]::new);
  }

  /** Returns the violations, known parameters first and unknown ones after, unmodifiable. */
  public List<FieldViolation> violations() {
    return List.of(violations);
  }
}

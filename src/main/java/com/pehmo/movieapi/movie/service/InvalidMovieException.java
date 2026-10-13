package com.pehmo.movieapi.movie.service;

import java.io.Serial;
import java.util.List;

/**
 * Thrown when a {@link NewMovie} breaks one or more movie rules. It carries every violation, not
 * only the first, so the API can answer {@code 400} listing all offending fields (ADR-0004).
 */
public final class InvalidMovieException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  // An array, not a List: exceptions are Serializable and List is not a serializable type.
  private final FieldViolation[] violations;

  /** Creates the exception; {@code violations} must not be empty. */
  public InvalidMovieException(List<FieldViolation> violations) {
    super("Invalid movie: " + violations);
    this.violations = violations.toArray(FieldViolation[]::new);
  }

  /** Returns the violations in field declaration order, unmodifiable. */
  public List<FieldViolation> violations() {
    return List.of(violations);
  }
}

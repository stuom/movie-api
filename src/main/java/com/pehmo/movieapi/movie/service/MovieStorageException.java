package com.pehmo.movieapi.movie.service;

import java.io.Serial;

/**
 * Thrown when the movie store fails or does not answer in time. It hides the storage technology
 * from callers, so the service and the API never see database types, and the error handler can
 * answer {@code 500} without leaking details (ADR-0010).
 */
public final class MovieStorageException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates the exception, keeping the original failure as the cause for the logs. */
  public MovieStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}

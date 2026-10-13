package com.pehmo.movieapi.movie.service;

import java.io.Serial;

/**
 * Thrown when a movie with the same normalized name and year already exists. The deterministic id
 * makes storage reject the second copy atomically, and this exception carries that outcome to the
 * API as {@code 409 Conflict} (ADR-0006).
 */
public final class DuplicateMovieException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String movieId;

  /** Creates the exception for the movie that could not be added. */
  public DuplicateMovieException(Movie movie) {
    super(
        "A movie named '%s' from %d already exists (id %s)"
            .formatted(movie.name(), movie.year(), movie.id()));
    this.movieId = movie.id();
  }

  /** Returns the id of the movie that already exists. */
  public String movieId() {
    return movieId;
  }
}

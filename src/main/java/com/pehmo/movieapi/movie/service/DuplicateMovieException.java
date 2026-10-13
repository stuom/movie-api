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
  private final String name;
  private final int year;

  /** Creates the exception for the movie that could not be added. */
  public DuplicateMovieException(Movie movie) {
    super(
        "A movie named '%s' from %d already exists (id %s)"
            .formatted(movie.name(), movie.year(), movie.id()));
    this.movieId = movie.id();
    this.name = movie.name();
    this.year = movie.year();
  }

  /** Returns the id of the movie that already exists. */
  public String movieId() {
    return movieId;
  }

  /**
   * Returns the name of the movie that could not be added, as submitted (trimmed). The stored movie
   * may spell it with different case or spacing.
   */
  public String name() {
    return name;
  }

  /** Returns the year of the movie that could not be added. */
  public int year() {
    return year;
  }
}

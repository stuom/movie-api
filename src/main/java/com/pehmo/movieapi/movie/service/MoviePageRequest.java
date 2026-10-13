package com.pehmo.movieapi.movie.service;

/**
 * Which page of the sorted movies to return. Valid by construction: request values are checked by
 * {@link MovieQuery#parse}, which reports them to the client, so an invalid value here is a bug.
 *
 * <p>Out-of-range values are rejected, never clamped: a client asking for 500 movies per page would
 * otherwise get 100 without noticing and could stop paging too early.
 *
 * @param page the 0-based page number
 * @param size the number of movies per page, 1 to {@link #MAX_SIZE}
 */
public record MoviePageRequest(int page, int size) {

  /** The page returned when the client does not ask for one. */
  public static final int DEFAULT_PAGE = 0;

  /** The page size used when the client does not ask for one. */
  public static final int DEFAULT_SIZE = 20;

  /** The largest page a client may ask for; it keeps a response at roughly 100 KB. */
  public static final int MAX_SIZE = 100;

  /** Rejects values {@link MovieQuery#parse} would have reported. */
  public MoviePageRequest {
    if (page < 0) {
      throw new IllegalArgumentException("page must be 0 or more, was " + page);
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new IllegalArgumentException(
          "size must be between 1 and " + MAX_SIZE + ", was " + size);
    }
  }
}

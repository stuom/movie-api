package com.pehmo.movieapi.movie.service;

import java.io.Serial;

/** Thrown when no movie in the catalog has the requested id; the API answers {@code 404}. */
public final class MovieNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String id;

  /** Creates the exception for the id that was asked for. */
  public MovieNotFoundException(String id) {
    // The id is client text, so it stays out of the message, which may end up in a log.
    super("No movie has the requested id");
    this.id = id;
  }

  /** Returns the id that was asked for, as the client sent it. */
  public String id() {
    return id;
  }
}

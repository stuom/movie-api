package com.pehmo.movieapi.movie.service;

import java.util.List;

/**
 * One page of sorted movies with the counts a client needs to page through all of them.
 *
 * @param items the movies on this page, in order, unmodifiable; empty past the last page
 * @param page the 0-based page number that was asked for
 * @param size the page size that was asked for
 * @param totalItems the number of movies on all pages together
 * @param totalPages the number of pages of {@code size} movies, 0 when there are no movies
 */
public record MoviePage(List<Movie> items, int page, int size, int totalItems, int totalPages) {

  /** Freezes the items, so a page cannot change after it was built. */
  public MoviePage {
    items = List.copyOf(items);
  }
}

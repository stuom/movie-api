package com.pehmo.movieapi.movie.service;

import java.util.Comparator;
import java.util.List;

/**
 * Sorts and pages the catalog in memory (ADR-0007). Plain Java on purpose, so every rule is covered
 * by fast unit tests. The search slices add the matching of one search field in front of the sort.
 */
public final class MovieSearch {

  /**
   * Name ignoring case, then id: a fixed order, so paging never skips or repeats a movie while the
   * catalog is unchanged. Normalizing inside the comparator costs a few thousand normalizations per
   * request for a catalog of about 500 movies (ADR-0013), which is not worth caching.
   */
  private static final Comparator<Movie> ORDER =
      Comparator.comparing((Movie movie) -> TextNormalization.normalize(movie.name()))
          .thenComparing(Movie::id);

  private MovieSearch() {}

  /**
   * Returns the requested page of the movies sorted by normalized name, then id. A page past the
   * end is empty but keeps the true totals, so a client can tell that it went too far rather than
   * that nothing exists.
   *
   * @param movies the movies to page through, in any order; not modified
   */
  public static MoviePage pageOf(List<Movie> movies, MoviePageRequest paging) {
    int totalItems = movies.size();
    int totalPages = Math.ceilDiv(totalItems, paging.size());
    // long: page * size overflows an int for large page numbers.
    long offset = (long) paging.page() * paging.size();
    List<Movie> items =
        offset >= totalItems
            ? List.of()
            : movies.stream().sorted(ORDER).skip(offset).limit(paging.size()).toList();
    return new MoviePage(items, paging.page(), paging.size(), totalItems, totalPages);
  }
}

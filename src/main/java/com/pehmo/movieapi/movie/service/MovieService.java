package com.pehmo.movieapi.movie.service;

import com.pehmo.movieapi.movie.persistence.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The movie use cases. API requests and the seed loader both come through here, so every movie gets
 * the same trimming, validation and duplicate check, and every stored movie reaches the in-memory
 * catalog.
 */
@Service
public class MovieService {

  private static final Logger LOG = LoggerFactory.getLogger(MovieService.class);

  private final MovieRepository repository;
  private final MovieRules rules;
  private final MovieCatalog catalog;

  /** Creates the service on the movie store, the movie rules and the in-memory catalog. */
  public MovieService(MovieRepository repository, MovieRules rules, MovieCatalog catalog) {
    this.repository = repository;
    this.rules = rules;
    this.catalog = catalog;
  }

  /**
   * Validates and stores a new movie, and returns it as stored.
   *
   * <p>There is deliberately no "does it exist already?" check before storing: the deterministic id
   * makes the store reject a duplicate atomically (ADR-0006), while a check beforehand could race
   * with a concurrent create and adds nothing.
   *
   * <p>The catalog is updated only after the store confirms the write. When the outcome is unknown
   * ({@link MovieStorageException}), the movie appears with the next catalog reload, if it was
   * stored.
   *
   * @throws InvalidMovieException if the movie breaks a rule; nothing is stored
   * @throws DuplicateMovieException if a movie with the same normalized name and year exists
   * @throws MovieStorageException if the store fails or does not answer in time; the movie may
   *     still have been stored (ADR-0010)
   */
  public Movie createMovie(NewMovie newMovie) {
    Movie movie = rules.toValidMovie(newMovie);
    repository.create(movie);
    catalog.include(movie);
    // Only the hex id and the year: the name is client text and could forge log lines.
    LOG.info("Added movie {} ({})", movie.id(), movie.year());
    return movie;
  }

  /**
   * Returns the movie with this id. It reads the catalog, like listing, so a movie is visible to
   * both or to neither (ADR-0007).
   *
   * @throws MovieNotFoundException if the catalog has no movie with this id
   */
  public Movie findMovie(String id) {
    return catalog.findById(id).orElseThrow(() -> new MovieNotFoundException(id));
  }

  /**
   * Returns the requested page of the catalog movies that match the query's criteria, sorted by
   * name and then id; with no search field, the page of the whole catalog.
   */
  public MoviePage searchMovies(MovieQuery query) {
    return MovieSearch.search(catalog.movies(), query.criteria(), query.paging());
  }
}

package com.pehmo.movieapi.movie.service;

import com.pehmo.movieapi.movie.persistence.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The movie use cases. API requests and (later) the seed loader both come through here, so every
 * movie gets the same trimming, validation and duplicate check.
 */
@Service
public class MovieService {

  private static final Logger LOG = LoggerFactory.getLogger(MovieService.class);

  private final MovieRepository repository;
  private final MovieRules rules;

  /** Creates the service on the movie store and the movie rules. */
  public MovieService(MovieRepository repository, MovieRules rules) {
    this.repository = repository;
    this.rules = rules;
  }

  /**
   * Validates and stores a new movie, and returns it as stored.
   *
   * <p>There is deliberately no "does it exist already?" check before storing: the deterministic id
   * makes the store reject a duplicate atomically (ADR-0006), while a check beforehand could race
   * with a concurrent create and adds nothing.
   *
   * @throws InvalidMovieException if the movie breaks a rule; nothing is stored
   * @throws DuplicateMovieException if a movie with the same normalized name and year exists
   * @throws MovieStorageException if the store fails or does not answer in time; the movie may
   *     still have been stored (ADR-0010)
   */
  public Movie createMovie(NewMovie newMovie) {
    Movie movie = rules.toValidMovie(newMovie);
    repository.create(movie);
    // Only the hex id and the year: the name is client text and could forge log lines.
    LOG.info("Added movie {} ({})", movie.id(), movie.year());
    return movie;
  }
}

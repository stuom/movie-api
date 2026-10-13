package com.pehmo.movieapi.movie.persistence;

import com.pehmo.movieapi.movie.service.DuplicateMovieException;
import com.pehmo.movieapi.movie.service.Movie;
import com.pehmo.movieapi.movie.service.MovieStorageException;
import java.util.List;

/**
 * The durable movie store. It can only add one movie and read all movies back.
 *
 * <p>There is deliberately no lookup by id and no filtered query: get by id and search run in
 * memory over the {@code MovieCatalog} snapshot, so they cost no reads and always agree with each
 * other (ADR-0007). A feature that seems to need a query belongs in {@code MovieSearch} instead.
 */
public interface MovieRepository {

  /**
   * Stores a new movie under its id, atomically refusing an id that is already taken.
   *
   * @throws DuplicateMovieException if a movie with the same id is already stored
   * @throws MovieStorageException if the store fails or does not answer in time
   */
  void create(Movie movie);

  /**
   * Reads every stored movie. Each call reads the whole collection, so call it only to load the
   * catalog, never per request.
   *
   * @return all movies, in no particular order
   * @throws MovieStorageException if the store fails, does not answer in time, or holds a document
   *     that is not a valid movie
   */
  List<Movie> findAll();
}

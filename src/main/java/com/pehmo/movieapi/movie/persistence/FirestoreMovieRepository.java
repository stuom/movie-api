package com.pehmo.movieapi.movie.persistence;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.pehmo.movieapi.movie.service.DuplicateMovieException;
import com.pehmo.movieapi.movie.service.Movie;
import com.pehmo.movieapi.movie.service.MovieStorageException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Repository;

/**
 * Stores movies in the Firestore {@code movies} collection, one document per movie keyed by its
 * deterministic id.
 *
 * <p>Deliberately thin, because there is no emulator and no automated test for it (ADR-0003): it
 * only calls Firestore, translates failures and delegates the mapping to {@link MovieDocument} and
 * the error classification to {@link FirestoreErrors}, which are unit tested. No Firestore type
 * leaves this class.
 */
@Repository
public class FirestoreMovieRepository implements MovieRepository {

  private static final String MOVIES_COLLECTION = "movies";

  /** Bounded wait, so a stuck call fails the request (or startup) instead of hanging it. */
  private static final long TIMEOUT_SECONDS = 20;

  private final Firestore firestore;

  /** Creates the repository on the application's Firestore client. */
  public FirestoreMovieRepository(Firestore firestore) {
    this.firestore = firestore;
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code DocumentReference.create} fails with {@code ALREADY_EXISTS} when the id is taken,
   * which gives atomic duplicate detection without a transaction (ADR-0006).
   *
   * <p>A timeout means the outcome is unknown, not that nothing was stored: the write may still
   * commit, and a retry then gets {@link DuplicateMovieException} (ADR-0010).
   */
  @Override
  public void create(Movie movie) {
    Map<String, Object> fields = new HashMap<>(MovieDocument.fieldsOf(movie));
    fields.put(MovieDocument.CREATED_AT, FieldValue.serverTimestamp());
    try {
      await(movies().document(movie.id()).create(fields));
    } catch (ExecutionException e) {
      if (FirestoreErrors.isAlreadyExists(e)) {
        throw new DuplicateMovieException(movie);
      }
      throw new MovieStorageException("Could not store movie " + movie.id(), e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<Movie> findAll() {
    List<QueryDocumentSnapshot> documents;
    try {
      documents = await(movies().get()).getDocuments();
    } catch (ExecutionException e) {
      throw new MovieStorageException("Could not read the movies", e);
    }
    List<Movie> result = new ArrayList<>(documents.size());
    for (QueryDocumentSnapshot document : documents) {
      result.add(toMovie(document));
    }
    return result;
  }

  private CollectionReference movies() {
    return firestore.collection(MOVIES_COLLECTION);
  }

  private static Movie toMovie(QueryDocumentSnapshot document) {
    try {
      return MovieDocument.toMovie(document.getId(), document.getData());
    } catch (IllegalStateException e) {
      throw new MovieStorageException("Stored document is not a valid movie", e);
    }
  }

  // Leaves ExecutionException to the caller, which knows what the operation was.
  private static <T> T await(ApiFuture<T> future) throws ExecutionException {
    try {
      return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MovieStorageException("Interrupted while waiting for Firestore", e);
    } catch (TimeoutException e) {
      future.cancel(true); // stop waiting on the abandoned call; cannot undo a commit
      throw new MovieStorageException(
          "Firestore did not answer within " + TIMEOUT_SECONDS + " seconds", e);
    }
  }
}

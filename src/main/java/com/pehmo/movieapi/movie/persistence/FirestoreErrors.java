package com.pehmo.movieapi.movie.persistence;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;

/**
 * Classifies Firestore failures. It is kept apart from {@link FirestoreMovieRepository}, which has
 * no automated tests, because this decision turns a duplicate into {@code 409} instead of {@code
 * 500} and must therefore be unit tested (ADR-0010).
 */
final class FirestoreErrors {

  private FirestoreErrors() {}

  /**
   * Tells whether the failure, or any of its causes, is Firestore refusing to create a document
   * whose id is already taken. Futures wrap the real error, so the whole cause chain is checked.
   */
  static boolean isAlreadyExists(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof ApiException api
          && api.getStatusCode().getCode() == StatusCode.Code.ALREADY_EXISTS) {
        return true;
      }
    }
    return false;
  }
}

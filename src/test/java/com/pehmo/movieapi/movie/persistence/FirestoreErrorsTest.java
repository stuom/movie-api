package com.pehmo.movieapi.movie.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.PermissionDeniedException;
import io.grpc.Status.Code;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class FirestoreErrorsTest {

  private static ApiException anAlreadyExistsError() {
    return new AlreadyExistsException(
        new RuntimeException("exists"), GrpcStatusCode.of(Code.ALREADY_EXISTS), false);
  }

  @Test
  void shouldRecognizeAlreadyExistsWhenItIsTheFailureItself() {
    // given
    Throwable failure = anAlreadyExistsError();

    // when
    boolean result = FirestoreErrors.isAlreadyExists(failure);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldRecognizeAlreadyExistsWhenWrappedInExecutionException() {
    // given
    Throwable failure = new ExecutionException(anAlreadyExistsError());

    // when
    boolean result = FirestoreErrors.isAlreadyExists(failure);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldRecognizeAlreadyExistsWhenWrappedTwoLevelsDeep() {
    // given
    Throwable failure =
        new RuntimeException("outer", new ExecutionException(anAlreadyExistsError()));

    // when
    boolean result = FirestoreErrors.isAlreadyExists(failure);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnFalseWhenStatusIsNotFound() {
    // given
    Throwable failure =
        new NotFoundException(
            new RuntimeException("missing"), GrpcStatusCode.of(Code.NOT_FOUND), false);

    // when
    boolean result = FirestoreErrors.isAlreadyExists(failure);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldNotRecognizePermissionDeniedAsAlreadyExistsWhenWrapped() {
    // given
    Throwable failure =
        new ExecutionException(
            new PermissionDeniedException(
                new RuntimeException("denied"), GrpcStatusCode.of(Code.PERMISSION_DENIED), false));

    // when
    boolean result = FirestoreErrors.isAlreadyExists(failure);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnFalseWhenFailureIsUnrelated() {
    // given
    Throwable failure = new IllegalStateException("boom", new RuntimeException("cause"));

    // when
    boolean result = FirestoreErrors.isAlreadyExists(failure);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnFalseWhenFailureIsNull() {
    // given
    Throwable failure = null;

    // when
    boolean result = FirestoreErrors.isAlreadyExists(failure);

    // then
    assertThat(result).isFalse();
  }
}

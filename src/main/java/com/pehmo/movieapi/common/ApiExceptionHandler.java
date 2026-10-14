package com.pehmo.movieapi.common;

import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.pehmo.movieapi.movie.service.DuplicateMovieException;
import com.pehmo.movieapi.movie.service.FieldViolation;
import com.pehmo.movieapi.movie.service.InvalidMovieException;
import com.pehmo.movieapi.movie.service.InvalidQueryException;
import com.pehmo.movieapi.movie.service.MovieNotFoundException;
import com.pehmo.movieapi.movie.service.MovieStorageException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The single place that turns an error into an RFC 7807 problem detail (ADR-0004).
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} gives Spring MVC's own errors (unknown path,
 * wrong method, wrong content type, missing body) the same format. Every {@code detail} is written
 * here, never copied from an exception, so internals such as class names, Jackson messages or
 * storage errors cannot leak; server errors are logged with their stack trace instead.
 *
 * <p>Problem members: {@code type} stays {@code about:blank} and {@code title} the status phrase;
 * {@code instance} is filled with the request path by Spring. Extensions: {@code violations} for
 * {@code 400} body and query parameter errors, and {@code movieId} for duplicates.
 *
 * <p>The catch-all {@link #handleUnexpected} runs before Spring's {@code @ResponseStatus}
 * resolution, so every new exception that should not be a {@code 500} needs its own handler here.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final String VIOLATIONS = "violations";
  private static final String MOVIE_ID = "movieId";

  private static final String UNREADABLE_BODY =
      "The request body is not a valid movie: it must be a JSON object with the documented fields"
          + " and types.";
  private static final String STORAGE_FAILURE =
      "The movie store failed or did not answer in time. If you were adding a movie, it may have"
          + " been saved; retrying is safe.";
  private static final String UNEXPECTED_ERROR = "An unexpected error occurred.";
  private static final String BODY_TOO_LARGE =
      "The request body is too large; a movie fits in " + JacksonConfig.MAX_BODY_KILOBYTES + " KB.";

  /** Named in Jackson's message when {@code StreamReadConstraints.maxDocumentLength} trips. */
  private static final String DOCUMENT_LENGTH_LIMIT = "getMaxDocumentLength";

  /** Jackson nests the limit error one or two levels deep; this leaves ample room. */
  private static final int MAX_CAUSE_DEPTH = 10;

  private static final String NO_ENDPOINT = "There is no endpoint at this path.";
  private static final String NO_MOVIE = "There is no movie with this id.";
  private static final String INVALID_QUERY = "The query parameters are invalid; see violations.";

  /** A movie that breaks rules: every violation, with its field path, in field order. */
  @ExceptionHandler(InvalidMovieException.class)
  public ProblemDetail handleInvalidMovie(InvalidMovieException exception) {
    List<ProblemViolation> violations = toProblemViolations(exception.violations());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "The movie breaks %d %s; see violations."
                .formatted(violations.size(), violations.size() == 1 ? "rule" : "rules"));
    problem.setProperty(VIOLATIONS, violations);
    return problem;
  }

  /**
   * Unusable query parameters: every problem, in this order: page, size, each search parameter in
   * table order, a lower bound above its upper bound, combined search fields, then unknown names.
   */
  @ExceptionHandler(InvalidQueryException.class)
  public ProblemDetail handleInvalidQuery(InvalidQueryException exception) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_QUERY);
    problem.setProperty(VIOLATIONS, toProblemViolations(exception.violations()));
    return problem;
  }

  /** An unknown id. The id is not repeated: it is client text, like the name in a duplicate. */
  @ExceptionHandler(MovieNotFoundException.class)
  public ProblemDetail handleMovieNotFound(MovieNotFoundException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, NO_MOVIE);
  }

  /** A second copy of an existing film; {@code movieId} lets the client find the existing one. */
  @ExceptionHandler(DuplicateMovieException.class)
  public ProblemDetail handleDuplicateMovie(DuplicateMovieException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "A movie with the same name and year as '%s' (%d) already exists."
                .formatted(exception.name(), exception.year()));
    problem.setProperty(MOVIE_ID, exception.movieId());
    return problem;
  }

  /**
   * The store failed. The detail tells a client that retrying a create is safe, because a timed-out
   * create may have been stored and a retry then answers {@code 409} (ADR-0010).
   */
  @ExceptionHandler(MovieStorageException.class)
  public ProblemDetail handleStorageFailure(MovieStorageException exception) {
    LOG.error("Movie store failure", exception);
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, STORAGE_FAILURE);
  }

  /** Anything not handled above is a bug or an outage: log it, reveal nothing. */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception exception) {
    LOG.error("Unexpected error", exception);
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR);
  }

  /**
   * A body that cannot be read as a movie: missing, not JSON, a wrong type, a number out of range
   * or an unknown field. The offending field is named when Jackson knows where the error is. A body
   * over the size limit is {@code 413}, so a client knows that fixing a field will not help.
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (isBodyTooLarge(exception)) {
      HttpStatus tooLarge = HttpStatus.PAYLOAD_TOO_LARGE;
      ProblemDetail problem = ProblemDetail.forStatusAndDetail(tooLarge, BODY_TOO_LARGE);
      return handleExceptionInternal(exception, problem, headers, tooLarge, request);
    }
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, UNREADABLE_BODY);
    if (exception.getCause() instanceof JsonMappingException mapping
        && !mapping.getPath().isEmpty()) {
      ProblemViolation violation =
          new ProblemViolation(JsonFieldPath.of(mapping.getPath()), problemWith(mapping));
      problem.setProperty(VIOLATIONS, List.of(violation));
    }
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  /** Spring's own wording names static resources, which means nothing to an API client. */
  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, NO_ENDPOINT);
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  private static List<ProblemViolation> toProblemViolations(List<FieldViolation> violations) {
    return violations.stream()
        .map(violation -> new ProblemViolation(violation.field(), violation.message()))
        .toList();
  }

  /**
   * Whether Jackson stopped because the body passed the size limit. The whole cause chain is
   * searched because Jackson wraps the limit error in a {@link JsonMappingException} when it trips
   * inside a field, which is where it trips for any body well over the limit. Jackson's other
   * stream limits (number length, nesting depth, name length) use the same exception type and can
   * trip in a small body, so they stay {@code 400}; Jackson 2 tells them apart only by the message,
   * which {@code MovieControllerTest} pins for both cases. The search stops after {@link
   * #MAX_CAUSE_DEPTH} causes, so a cyclic chain cannot hold a request thread forever.
   */
  private static boolean isBodyTooLarge(Throwable exception) {
    Throwable cause = exception.getCause();
    for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (cause instanceof StreamConstraintsException limit) {
        return isDocumentLengthLimit(limit);
      }
      cause = cause.getCause();
    }
    return false;
  }

  /** Jackson 2 has no subtype per limit, so the message is the only way to tell them apart. */
  private static boolean isDocumentLengthLimit(StreamConstraintsException limit) {
    String message = limit.getMessage();
    return message != null && message.contains(DOCUMENT_LENGTH_LIMIT);
  }

  private static String problemWith(JsonMappingException mapping) {
    if (mapping instanceof UnrecognizedPropertyException) {
      return "is not a known field";
    }
    if (mapping.getCause() instanceof InputCoercionException) {
      return "is out of range";
    }
    return "has the wrong type";
  }
}

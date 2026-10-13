package com.pehmo.movieapi.common;

import java.io.Serializable;

/**
 * One entry of the {@code violations} member of a {@code 400} problem detail. It is the API's own
 * type, separate from the service's {@code FieldViolation}, so a change inside the service cannot
 * silently change the error format (ADR-0004).
 *
 * @param field the path of the offending field, for example {@code actors[2].lastName}
 * @param message what is wrong, for a human reader
 */
public record ProblemViolation(String field, String message) implements Serializable {}

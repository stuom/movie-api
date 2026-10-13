package com.pehmo.movieapi.movie.service;

import java.io.Serializable;

/**
 * One broken rule in a {@link NewMovie}, reported to the client so it can fix every field at once.
 *
 * @param field the path of the offending field, for example {@code genres[1]} or {@code
 *     actors[2].lastName}
 * @param message what is wrong, for a human reader
 */
public record FieldViolation(String field, String message) implements Serializable {}

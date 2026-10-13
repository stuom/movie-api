package com.pehmo.movieapi.movie.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Derives the deterministic id of a movie from its name and year.
 *
 * <p>The same film always gets the same id, so storing it with a create-only operation rejects a
 * second copy atomically, without a query or a transaction (ADR-0006). The id is a plain {@code
 * String}; this class only holds the derivation.
 *
 * <p>The algorithm is frozen because ids are persisted: the first 32 lowercase hex characters of
 * SHA-256 over the UTF-8 bytes of {@code normalize(name) + "|" + year}. {@code MovieIdTest} pins
 * golden values computed outside the code.
 */
public final class MovieId {

  /** 16 bytes = 32 hex characters = 128 bits, plenty to avoid collisions at catalog scale. */
  private static final int ID_BYTES = 16;

  private MovieId() {}

  /** Returns the id for a movie with this name and release year. */
  public static String derivedFrom(String name, int year) {
    String key = TextNormalization.normalize(name) + "|" + year;
    byte[] hash = sha256().digest(key.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash, 0, ID_BYTES);
  }

  // A new instance per call: MessageDigest is not thread-safe.
  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every Java runtime must provide SHA-256", e);
    }
  }
}

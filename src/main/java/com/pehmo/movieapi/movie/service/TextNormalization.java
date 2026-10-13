package com.pehmo.movieapi.movie.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The single definition of how movie text is cleaned and compared.
 *
 * <p>Whitespace means {@link Character#isWhitespace(char)} everywhere, so trimming and collapsing
 * agree. The non-breaking space (U+00A0) is deliberately not whitespace under that definition.
 *
 * <p>{@link #normalize(String)} feeds the persisted movie id ({@link MovieId}), so its behavior is
 * frozen: changing it would give existing movies different ids (ADR-0006).
 */
public final class TextNormalization {

  // \p{javaWhitespace} is Character.isWhitespace, the same definition String.strip() uses.
  private static final Pattern WHITESPACE_RUN = Pattern.compile("\\p{javaWhitespace}+");

  private TextNormalization() {}

  /**
   * Returns the comparison form of {@code text}: Unicode NFC, trimmed, inner whitespace runs
   * collapsed to one space, lowercased with {@link java.util.Locale#ROOT}. Accents are kept.
   *
   * <p>NFC makes a precomposed "é" and "e" + combining accent equal, so visually identical names
   * get the same id.
   *
   * @throws NullPointerException if {@code text} is null; callers decide what absent text means
   */
  public static String normalize(String text) {
    Objects.requireNonNull(text, "text");
    String composed = Normalizer.normalize(text, Normalizer.Form.NFC);
    return WHITESPACE_RUN.matcher(composed.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
  }

  /**
   * Removes leading and trailing whitespace only, keeping case and inner spacing, because stored
   * text keeps the form the client entered.
   *
   * @throws NullPointerException if {@code text} is null
   */
  public static String trim(String text) {
    return text.strip();
  }
}

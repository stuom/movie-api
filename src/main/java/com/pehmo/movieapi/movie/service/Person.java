package com.pehmo.movieapi.movie.service;

import java.util.Objects;

/**
 * A director or actor of a valid movie. Names are already trimmed and checked by {@link
 * MovieRules}; multi-word last names such as "Lee Jones" are normal.
 *
 * @param firstName the first name, never null
 * @param lastName the last name, never null
 */
public record Person(String firstName, String lastName) {

  /** Rejects missing names, so code using a person never needs null checks. */
  public Person {
    Objects.requireNonNull(firstName, "firstName");
    Objects.requireNonNull(lastName, "lastName");
  }
}

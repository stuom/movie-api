package com.pehmo.movieapi.movie.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A movie to add, exactly as received from the API or the seed file, before any checks.
 *
 * <p>Every field may be null and lists may contain null entries, so {@link MovieRules} can report
 * each problem with its field path instead of failing on the first one. The lists are copied, so
 * later changes to the caller's lists have no effect.
 *
 * @param name the name as entered
 * @param year the release year
 * @param genres the genres as entered
 * @param ageLimit the minimum recommended viewer age
 * @param rating the rating in stars
 * @param director the director, or null
 * @param actors the actors in billing order, or null
 * @param synopsis the synopsis, or null
 */
public record NewMovie(
    String name,
    Integer year,
    List<String> genres,
    Integer ageLimit,
    Integer rating,
    PersonInput director,
    List<PersonInput> actors,
    String synopsis) {

  /** Copies the lists while keeping null lists and null entries for {@link MovieRules}. */
  public NewMovie {
    // Not List.copyOf: it rejects null entries, which must reach MovieRules to be reported.
    genres = genres == null ? null : Collections.unmodifiableList(new ArrayList<>(genres));
    actors = actors == null ? null : Collections.unmodifiableList(new ArrayList<>(actors));
  }

  /**
   * A director or actor as entered; either name may be missing or blank.
   *
   * @param firstName the first name as entered
   * @param lastName the last name as entered
   */
  public record PersonInput(String firstName, String lastName) {}
}

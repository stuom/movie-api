package com.pehmo.movieapi.movie.service;

import java.util.List;
import java.util.Objects;

/**
 * A movie as the service stores, searches and returns it.
 *
 * <p>Instances are trusted and structurally complete: validity comes from {@link
 * MovieRules#toValidMovie(NewMovie)}, and is deliberately not re-checked here. Movies loaded from
 * storage use this constructor too, so a rule tightened later cannot break loading movies that were
 * valid when they were added (ADR-0009).
 *
 * @param id the deterministic id from {@link MovieId#derivedFrom(String, int)}
 * @param name the trimmed name
 * @param year the release year
 * @param genres the trimmed genres, unmodifiable
 * @param ageLimit the minimum recommended viewer age in years
 * @param rating the rating in stars
 * @param director the director, or null when unknown
 * @param actors the actors in billing order, unmodifiable, empty when none are known
 * @param synopsis the trimmed synopsis, or null when there is none
 */
public record Movie(
    String id,
    String name,
    int year,
    List<String> genres,
    int ageLimit,
    int rating,
    Person director,
    List<Person> actors,
    String synopsis) {

  /** Requires the structurally mandatory parts and freezes the lists. */
  public Movie {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    genres = List.copyOf(genres);
    actors = List.copyOf(actors);
  }
}

package com.pehmo.movieapi.movie.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link NewMovie} test data. Defaults are valid, so each test states only the field it is
 * about.
 */
final class MovieTestData {

  private String name = "Aliens";
  private Integer year = 1986;
  private List<String> genres = new ArrayList<>(List.of("Action", "Sci-fi"));
  private Integer ageLimit = 16;
  private Integer rating = 5;
  private NewMovie.PersonInput director = aPerson("James", "Cameron");
  private List<NewMovie.PersonInput> actors =
      new ArrayList<>(List.of(aPerson("Sigourney", "Weaver")));
  private String synopsis = "Ellen Ripley is rescued by a deep salvage team.";

  private MovieTestData() {}

  /** Starts a valid new movie. */
  static MovieTestData aNewMovie() {
    return new MovieTestData();
  }

  /** Creates a person input as entered. */
  static NewMovie.PersonInput aPerson(String firstName, String lastName) {
    return new NewMovie.PersonInput(firstName, lastName);
  }

  /** Returns a text of exactly {@code length} characters. */
  static String textOfLength(int length) {
    return "a".repeat(length);
  }

  MovieTestData withName(String newName) {
    this.name = newName;
    return this;
  }

  MovieTestData withYear(Integer newYear) {
    this.year = newYear;
    return this;
  }

  MovieTestData withGenres(List<String> newGenres) {
    this.genres = newGenres;
    return this;
  }

  MovieTestData withAgeLimit(Integer newAgeLimit) {
    this.ageLimit = newAgeLimit;
    return this;
  }

  MovieTestData withRating(Integer newRating) {
    this.rating = newRating;
    return this;
  }

  MovieTestData withDirector(NewMovie.PersonInput newDirector) {
    this.director = newDirector;
    return this;
  }

  MovieTestData withActors(List<NewMovie.PersonInput> newActors) {
    this.actors = newActors;
    return this;
  }

  MovieTestData withSynopsis(String newSynopsis) {
    this.synopsis = newSynopsis;
    return this;
  }

  NewMovie build() {
    return new NewMovie(name, year, genres, ageLimit, rating, director, actors, synopsis);
  }
}

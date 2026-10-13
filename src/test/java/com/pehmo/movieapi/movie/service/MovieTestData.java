package com.pehmo.movieapi.movie.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link NewMovie} (raw input) and {@link Movie} (stored) test data. Defaults are valid, so
 * each test states only the field it is about.
 */
public final class MovieTestData {

  private String id;
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
  public static MovieTestData aNewMovie() {
    return new MovieTestData();
  }

  /** Starts a valid movie, to be finished with {@link #buildMovie()}. */
  public static MovieTestData aMovie() {
    return new MovieTestData();
  }

  /** Creates a person input as entered. */
  public static NewMovie.PersonInput aPerson(String firstName, String lastName) {
    return new NewMovie.PersonInput(firstName, lastName);
  }

  /** Returns a text of exactly {@code length} characters. */
  public static String textOfLength(int length) {
    return "a".repeat(length);
  }

  /**
   * Overrides the id that {@link #buildMovie()} derives from name and year, for tests that need
   * several movies whose names are equal after normalization.
   */
  public MovieTestData withId(String newId) {
    this.id = newId;
    return this;
  }

  public MovieTestData withName(String newName) {
    this.name = newName;
    return this;
  }

  public MovieTestData withYear(Integer newYear) {
    this.year = newYear;
    return this;
  }

  public MovieTestData withGenres(List<String> newGenres) {
    this.genres = newGenres == null ? null : new ArrayList<>(newGenres);
    return this;
  }

  public MovieTestData withAgeLimit(Integer newAgeLimit) {
    this.ageLimit = newAgeLimit;
    return this;
  }

  public MovieTestData withRating(Integer newRating) {
    this.rating = newRating;
    return this;
  }

  public MovieTestData withDirector(NewMovie.PersonInput newDirector) {
    this.director = newDirector;
    return this;
  }

  public MovieTestData withActors(List<NewMovie.PersonInput> newActors) {
    this.actors = newActors == null ? null : new ArrayList<>(newActors);
    return this;
  }

  public MovieTestData withSynopsis(String newSynopsis) {
    this.synopsis = newSynopsis;
    return this;
  }

  public NewMovie build() {
    return new NewMovie(name, year, genres, ageLimit, rating, director, actors, synopsis);
  }

  /**
   * Builds a stored {@link Movie} directly, with the id derived from name and year and the texts
   * exactly as given (no trimming).
   */
  public Movie buildMovie() {
    return new Movie(
        id != null ? id : MovieId.derivedFrom(name, year),
        name,
        year,
        genres == null ? List.of() : genres,
        ageLimit,
        rating,
        director == null ? null : toPerson(director),
        actors == null ? List.of() : actors.stream().map(MovieTestData::toPerson).toList(),
        synopsis);
  }

  private static Person toPerson(NewMovie.PersonInput input) {
    return new Person(input.firstName(), input.lastName());
  }
}

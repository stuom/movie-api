package com.pehmo.movieapi.movie.web;

import com.pehmo.movieapi.movie.service.Movie;
import com.pehmo.movieapi.movie.service.NewMovie;
import com.pehmo.movieapi.movie.service.Person;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps between the API's DTO records and the service's domain types, so the controller only
 * delegates and the domain never reaches the JSON layer.
 */
final class MovieMapper {

  private MovieMapper() {}

  /**
   * Copies the request as it is. Nulls, including null list entries, are kept on purpose so that
   * {@code MovieRules} reports them with their field path instead of the mapper failing first.
   */
  static NewMovie toNewMovie(NewMovieRequest request) {
    return new NewMovie(
        request.name(),
        request.year(),
        request.genres(),
        request.ageLimit(),
        request.rating(),
        toPersonInput(request.director()),
        toPersonInputs(request.actors()),
        request.synopsis());
  }

  /** Returns the movie as the API shows it: every field present, unknown parts null or empty. */
  static MovieResponse toResponse(Movie movie) {
    return new MovieResponse(
        movie.id(),
        movie.name(),
        movie.year(),
        movie.genres(),
        movie.ageLimit(),
        movie.rating(),
        toDto(movie.director()),
        movie.actors().stream().map(MovieMapper::toDto).toList(),
        movie.synopsis());
  }

  private static NewMovie.PersonInput toPersonInput(PersonDto person) {
    return person == null ? null : new NewMovie.PersonInput(person.firstName(), person.lastName());
  }

  // Not Stream.toList() alone: the result must keep null entries and stay null for a null list.
  private static List<NewMovie.PersonInput> toPersonInputs(List<PersonDto> people) {
    if (people == null) {
      return null;
    }
    List<NewMovie.PersonInput> inputs = new ArrayList<>(people.size());
    for (PersonDto person : people) {
      inputs.add(toPersonInput(person));
    }
    return inputs;
  }

  private static PersonDto toDto(Person person) {
    return person == null ? null : new PersonDto(person.firstName(), person.lastName());
  }
}

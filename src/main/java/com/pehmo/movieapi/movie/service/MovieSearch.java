package com.pehmo.movieapi.movie.service;

import com.pehmo.movieapi.movie.service.MovieSearchCriteria.ActorNamed;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.All;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.DirectorNamed;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.Genre;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.NameContains;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.RatingRange;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.SynopsisContains;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.ViewerAge;
import com.pehmo.movieapi.movie.service.MovieSearchCriteria.YearRange;
import java.util.Comparator;
import java.util.List;

/**
 * Searches, sorts and pages the catalog in memory (ADR-0007). Plain Java on purpose, so every rule
 * is covered by fast unit tests. Movie text is normalized at search time and the criteria hold
 * their text normalized, so matching ignores case: a substring for name, people and synopsis, an
 * exact match for genre.
 */
public final class MovieSearch {

  /**
   * Name ignoring case, then id: a fixed order, so paging never skips or repeats a movie while the
   * catalog is unchanged. Normalizing inside the comparator costs a few thousand normalizations per
   * request for a catalog of about 500 movies (ADR-0013), which is not worth caching.
   */
  private static final Comparator<Movie> ORDER =
      Comparator.comparing((Movie movie) -> TextNormalization.normalize(movie.name()))
          .thenComparing(Movie::id);

  private MovieSearch() {}

  /**
   * Returns the requested page of the movies that match the criteria, sorted by normalized name,
   * then id. The totals count the matches. A page past the end is empty but keeps the true totals,
   * so a client can tell that it went too far rather than that nothing exists.
   *
   * @param movies the movies to search, in any order; not modified
   */
  public static MoviePage search(
      List<Movie> movies, MovieSearchCriteria criteria, MoviePageRequest paging) {
    List<Movie> matching = movies.stream().filter(movie -> matches(movie, criteria)).toList();
    int totalItems = matching.size();
    int totalPages = Math.ceilDiv(totalItems, paging.size());
    // long: page * size overflows an int for large page numbers.
    long offset = (long) paging.page() * paging.size();
    List<Movie> items =
        offset >= totalItems
            ? List.of()
            : matching.stream().sorted(ORDER).skip(offset).limit(paging.size()).toList();
    return new MoviePage(items, paging.page(), paging.size(), totalItems, totalPages);
  }

  /**
   * Tells whether the movie matches the criteria. No {@code default} branch on purpose: a new
   * search field does not compile until it is handled here.
   */
  static boolean matches(Movie movie, MovieSearchCriteria criteria) {
    return switch (criteria) {
      case All() -> true;
      case NameContains(String text) -> nameContains(movie, text);
      case YearRange(int min, int max) -> isReleasedWithin(movie, min, max);
      case Genre(String text) -> hasGenre(movie, text);
      case ViewerAge(int age) -> isAllowedForViewerAged(movie, age);
      case RatingRange(int min, int max) -> isRatedWithin(movie, min, max);
      case DirectorNamed(List<String> words) -> isDirectedBy(movie, words);
      case ActorNamed(List<String> words) -> hasActorNamed(movie, words);
      case SynopsisContains(List<String> words) -> synopsisContainsEvery(movie, words);
    };
  }

  private static boolean nameContains(Movie movie, String text) {
    return TextNormalization.normalize(movie.name()).contains(text);
  }

  private static boolean isReleasedWithin(Movie movie, int min, int max) {
    return movie.year() >= min && movie.year() <= max;
  }

  /** An exact genre, not a substring: "sci" must not find "Sci-fi". */
  private static boolean hasGenre(Movie movie, String text) {
    return movie.genres().stream().map(TextNormalization::normalize).anyMatch(text::equals);
  }

  /** The age limit is the minimum recommended age, so a viewer of exactly that age may watch. */
  private static boolean isAllowedForViewerAged(Movie movie, int age) {
    return movie.ageLimit() <= age;
  }

  private static boolean isRatedWithin(Movie movie, int min, int max) {
    return movie.rating() >= min && movie.rating() <= max;
  }

  private static boolean isDirectedBy(Movie movie, List<String> words) {
    return movie.director() != null && matchesEveryWord(movie.director(), words);
  }

  /**
   * Each actor is checked on their own, so the words must all match the same person: "ryan wright"
   * does not find a cast of Ryan Gosling and Robin Wright.
   */
  private static boolean hasActorNamed(Movie movie, List<String> words) {
    return movie.actors().stream().anyMatch(actor -> matchesEveryWord(actor, words));
  }

  /** Each word is looked for on its own, so their order in the synopsis does not matter. */
  private static boolean synopsisContainsEvery(Movie movie, List<String> words) {
    if (movie.synopsis() == null) {
      return false;
    }
    String synopsis = TextNormalization.normalize(movie.synopsis());
    return words.stream().allMatch(synopsis::contains);
  }

  /** Every word is in the person's first name or last name, so "lee jones" finds "Lee Jones". */
  private static boolean matchesEveryWord(Person person, List<String> words) {
    String firstName = TextNormalization.normalize(person.firstName());
    String lastName = TextNormalization.normalize(person.lastName());
    return words.stream().allMatch(word -> firstName.contains(word) || lastName.contains(word));
  }
}

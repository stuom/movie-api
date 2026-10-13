package com.pehmo.movieapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Movie API, a REST service to list, search and add movies.
 *
 * <p>Kept free of logic: it is excluded from the coverage rule (ADR-0008), so anything worth
 * testing belongs in a feature package instead.
 */
@SpringBootApplication
public class MovieApiApplication {

  /**
   * Starts the embedded web server; the port comes from {@code $PORT} (Cloud Run) or defaults to
   * 8080.
   */
  public static void main(String[] args) {
    SpringApplication.run(MovieApiApplication.class, args);
  }
}

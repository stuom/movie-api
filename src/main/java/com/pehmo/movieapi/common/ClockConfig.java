package com.pehmo.movieapi.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application clock. Time-dependent rules (the year ceiling in {@code MovieRules}, the
 * catalog refresh) take a {@link Clock} so tests can fix the time (ADR-0009).
 */
@Configuration
public class ClockConfig {

  /** UTC, so the current year does not depend on the server's time zone. */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}

package com.pehmo.movieapi.movie.service;

import com.pehmo.movieapi.movie.persistence.MovieRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The in-memory copy of every movie that search and lookups read, so requests never read Firestore
 * (ADR-0007).
 *
 * <p>The copy loads when the application starts, absorbs each movie this instance adds, and reloads
 * from the store once it is older than the refresh interval. Reloading is lazy: the first access
 * after the interval reloads on the request thread, so an idle instance costs no reads and Cloud
 * Run's throttled background CPU cannot stall a scheduled reload. A failed reload never empties the
 * catalog: the previous copy is served and the next attempt waits a full interval, because a failed
 * load can still be billed for every document it read (ADR-0013).
 *
 * <p>Final because the constructor loads from the store and may throw; a subclass could otherwise
 * observe a partly constructed catalog (SpotBugs CT_CONSTRUCTOR_THROW).
 */
@Component
public final class MovieCatalog {

  private static final Logger LOG = LoggerFactory.getLogger(MovieCatalog.class);

  private final MovieRepository repository;
  private final Clock clock;
  private final Duration refreshInterval;

  private final AtomicReference<Snapshot> snapshot;
  // Non-reentrant on purpose: a read during a load (even on the same thread) serves the old copy.
  private final AtomicBoolean loading = new AtomicBoolean();
  private final AtomicReference<Instant> nextLoadAttemptAt;

  /**
   * Creates the catalog and loads it. A failed first load fails startup, because it usually means a
   * missing database or permission that no retry will fix.
   *
   * @param refreshInterval how old the copy may get before the next access reloads it; must be
   *     positive, since zero would read the whole collection on every request
   * @throws MovieStorageException if the first load fails
   */
  public MovieCatalog(
      MovieRepository repository,
      Clock clock,
      @Value("${app.catalog.refresh-interval}") Duration refreshInterval) {
    if (refreshInterval.isZero() || refreshInterval.isNegative()) {
      throw new IllegalArgumentException(
          "app.catalog.refresh-interval must be positive, was " + refreshInterval);
    }
    this.repository = repository;
    this.clock = clock;
    this.refreshInterval = refreshInterval;
    Instant start = clock.instant();
    this.snapshot = new AtomicReference<>(Snapshot.of(repository.findAll()));
    this.nextLoadAttemptAt = new AtomicReference<>(start.plus(refreshInterval));
    LOG.info("Catalog loaded with {} movies", snapshot.get().movies().size());
  }

  /** Returns every movie, unmodifiable and in no particular order; reloads first if stale. */
  public List<Movie> movies() {
    reloadIfStale();
    return snapshot.get().movies();
  }

  /** Returns the movie with this id, if the catalog has it; reloads first if stale. */
  public Optional<Movie> findById(String id) {
    reloadIfStale();
    return Optional.ofNullable(snapshot.get().byId().get(id));
  }

  /**
   * Makes a movie this instance has just stored visible at once, without a reload. A movie with the
   * same id replaces the existing entry, because a reload may already have picked it up. The next
   * reload time does not change.
   */
  public void include(Movie movie) {
    snapshot.updateAndGet(current -> current.with(List.of(movie)));
  }

  private void reloadIfStale() {
    if (clock.instant().isBefore(nextLoadAttemptAt.get())) {
      return;
    }
    if (!loading.compareAndSet(false, true)) {
      return; // another request is loading; serve the current copy meanwhile
    }
    try {
      // Checked again: another request may have finished a reload since the check above.
      if (clock.instant().isBefore(nextLoadAttemptAt.get())) {
        return;
      }
      reload();
    } finally {
      loading.set(false);
    }
  }

  private void reload() {
    Snapshot observed = snapshot.get();
    nextLoadAttemptAt.set(clock.instant().plus(refreshInterval));
    List<Movie> loaded;
    try {
      loaded = repository.findAll();
    } catch (MovieStorageException e) {
      LOG.warn(
          "Catalog reload failed; serving the previous {} movies until the next attempt at {}",
          observed.movies().size(),
          nextLoadAttemptAt.get(),
          e);
      return;
    }
    Snapshot fresh = Snapshot.of(loaded);
    // Movies added while the store was being read may be missing from what it returned.
    Snapshot published = snapshot.updateAndGet(current -> fresh.with(current.addedSince(observed)));
    LOG.info("Catalog reloaded with {} movies", published.movies().size());
  }

  /** One immutable copy of the catalog, keyed by id in the order the movies arrived. */
  private record Snapshot(Map<String, Movie> byId, List<Movie> movies) {

    static Snapshot of(Collection<Movie> movies) {
      return new Snapshot(Map.of(), List.of()).with(movies);
    }

    Snapshot with(Collection<Movie> additions) {
      if (additions.isEmpty()) {
        return this;
      }
      Map<String, Movie> merged = new LinkedHashMap<>(byId);
      for (Movie movie : additions) {
        merged.put(movie.id(), movie);
      }
      return new Snapshot(Collections.unmodifiableMap(merged), List.copyOf(merged.values()));
    }

    /** The movies in this snapshot whose ids {@code earlier} does not have. */
    List<Movie> addedSince(Snapshot earlier) {
      return movies.stream().filter(movie -> !earlier.byId.containsKey(movie.id())).toList();
    }
  }
}

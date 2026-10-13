package com.pehmo.movieapi.movie.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Adds the sample movies on startup when {@code app.seed.enabled=true} and the catalog is empty
 * (ADR-0006).
 *
 * <p>Every entry goes through {@link MovieService#createMovie}, so sample data gets exactly the
 * trimming, validation and duplicate check of API input. The sample file has known mistakes that
 * are deliberately not corrected here. An entry that cannot be added is logged with its index and
 * skipped, never dropped silently and never fatal. A storage failure stops seeding instead: the
 * remaining entries would almost certainly fail too, and waiting for each one could exceed Cloud
 * Run's startup limit.
 *
 * <p>Runs after the web server has started, so early requests may see a partly seeded catalog.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class MovieSeedLoader implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(MovieSeedLoader.class);

  private final MovieService movieService;
  private final MovieCatalog catalog;
  private final ObjectReader movieReader;
  private final Resource seedFile;

  /**
   * Creates the loader. It uses the application's strict {@link ObjectMapper} (ADR-0012), so a
   * sample entry with a wrong type or an unknown field is rejected just like an API request.
   */
  public MovieSeedLoader(
      MovieService movieService,
      MovieCatalog catalog,
      ObjectMapper objectMapper,
      @Value("classpath:seed/movies.json") Resource seedFile) {
    this.movieService = movieService;
    this.catalog = catalog;
    // An ObjectReader is immutable and keeps every setting of the mapper it comes from.
    this.movieReader = objectMapper.readerFor(NewMovie.class);
    this.seedFile = seedFile;
  }

  /**
   * Seeds the catalog if it is empty.
   *
   * @throws IllegalStateException if the seed file is unreadable or not a JSON array; it ships in
   *     the jar, so that is a build defect and startup should fail
   */
  @Override
  public void run(ApplicationArguments args) {
    int existing = catalog.movies().size();
    if (existing > 0) {
      LOG.info("Catalog already has {} movies; seeding skipped", existing);
      return;
    }
    JsonNode entries = readEntries();
    int added = 0;
    int skipped = 0;
    int index = 0;
    for (JsonNode entry : entries) {
      try {
        movieService.createMovie(movieReader.treeToValue(entry, NewMovie.class));
        added++;
      } catch (JsonProcessingException e) {
        LOG.warn("Seed entry {} skipped: not a movie: {}", index, e.getOriginalMessage());
        skipped++;
      } catch (InvalidMovieException e) {
        LOG.warn("Seed entry {} skipped: breaks movie rules: {}", index, e.violations());
        skipped++;
      } catch (DuplicateMovieException e) {
        LOG.warn("Seed entry {} skipped: duplicate of movie {}", index, e.movieId());
        skipped++;
      } catch (MovieStorageException e) {
        LOG.warn(
            "Seeding stopped at entry {}: the movie store failed; {} entries not attempted",
            index,
            entries.size() - index - 1,
            e);
        break;
      }
      index++;
    }
    logSummary(entries.size(), added, skipped);
  }

  private JsonNode readEntries() {
    JsonNode entries;
    try (InputStream input = seedFile.getInputStream()) {
      entries = movieReader.readTree(input);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read the seed file " + seedFile, e);
    }
    if (entries == null || !entries.isArray()) {
      throw new IllegalStateException("The seed file " + seedFile + " is not a JSON array");
    }
    return entries;
  }

  private static void logSummary(int total, int added, int skipped) {
    // Non-zero only when a storage failure stopped seeding; includes the entry that failed.
    int notAdded = total - added - skipped;
    if (added == total) {
      LOG.info("Seeded {} of {} sample movies", added, total);
    } else {
      LOG.warn(
          "Seeded {} of {} sample movies; {} skipped, {} left after a storage failure",
          added,
          total,
          skipped,
          notAdded);
    }
  }
}

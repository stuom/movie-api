package com.pehmo.movieapi.common;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Completes the strict JSON reading set up in {@code application.properties} (ADR-0012). It
 * configures the application's one {@code ObjectMapper}, so everything that reads JSON with it gets
 * the same limits.
 */
@Configuration
public class JacksonConfig {

  /**
   * Largest JSON document read, in kilobytes. The largest valid movie is about 10,000 characters,
   * so this leaves ample room, while a hostile body (Cloud Run accepts up to 32 MiB) cannot fill
   * the heap of the single instance before {@code MovieRules} rejects it.
   */
  static final int MAX_BODY_KILOBYTES = 256;

  private static final int BYTES_PER_KILOBYTE = 1024;

  /**
   * Bounds the document size and rejects numbers and booleans where text is expected. The latter is
   * needed because {@code allow-coercion-of-scalars=false} only stops text from becoming a number;
   * the other direction ({@code "name": 123} becoming {@code "123"}) has no property.
   */
  @Bean
  public Jackson2ObjectMapperBuilderCustomizer strictJsonReading() {
    return builder ->
        builder.postConfigurer(
            mapper -> {
              mapper
                  .getFactory()
                  .setStreamReadConstraints(
                      StreamReadConstraints.builder()
                          .maxDocumentLength((long) MAX_BODY_KILOBYTES * BYTES_PER_KILOBYTE)
                          .build());
              mapper
                  .coercionConfigFor(LogicalType.Textual)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            });
  }
}

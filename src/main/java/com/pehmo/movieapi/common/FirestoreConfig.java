package com.pehmo.movieapi.common;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Firestore client. Deliberately trivial and untested (ADR-0003, ADR-0008): on Cloud
 * Run, Application Default Credentials supply the runtime service account and the project, so there
 * are no key files and no project settings here.
 */
@Configuration
public class FirestoreConfig {

  /** One shared client for the {@code (default)} database, the only one with a free tier. */
  @Bean
  public Firestore firestore() {
    return FirestoreOptions.getDefaultInstance().getService();
  }
}

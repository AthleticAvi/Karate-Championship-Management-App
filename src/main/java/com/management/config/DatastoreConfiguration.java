package com.management.config;

import com.mongodb.WriteConcern;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The connection guarantees every environment gets, whatever its connection details say.
 *
 * <p>These live here and not in a connection string because a connection string is replaced per
 * environment: {@code @ServiceConnection} swaps it in the tests, so a test of a URI option would
 * prove nothing about the application. This customizer runs after Boot applies the connection
 * details, so it also wins over a weaker value in one.
 */
@Configuration(proxyBeanMethods = false)
class DatastoreConfiguration {

  @Bean
  MongoClientSettingsBuilderCustomizer datastoreGuarantees(
      DatastoreProperties properties, DatastoreGuaranteeCheck check) {
    return settings ->
        settings
            .writeConcern(WriteConcern.MAJORITY)
            .timeout(properties.operationTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .applyToServerSettings(server -> server.addServerListener(check));
  }
}

package com.management.config;

import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * The connection guarantees every environment gets, whatever its connection details say.
 *
 * <p>These live here and not in a connection string because a connection string is replaced per
 * environment: {@code @ServiceConnection} swaps it in the tests, so a test of a URI option would
 * prove nothing about the application. This customizer runs after Boot applies the connection
 * details, so it also wins over a weaker value in one.
 *
 * <p>There is no socket timeout: with an operation timeout set, the driver ignores it.
 */
@Configuration(proxyBeanMethods = false)
class DatastoreConfiguration {

  @Bean
  MongoClientSettingsBuilderCustomizer datastoreGuarantees(
      DatastoreProperties properties, DatastoreGuaranteeCheck check) {
    return settings ->
        settings
            .writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary())
            .retryWrites(true)
            .timeout(properties.operationTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .applyToSocketSettings(socket ->
                socket.connectTimeout(
                    properties.connectTimeout().toMillis(), TimeUnit.MILLISECONDS))
            .applyToClusterSettings(cluster ->
                cluster.serverSelectionTimeout(
                    properties.serverSelectionTimeout().toMillis(), TimeUnit.MILLISECONDS))
            .applyToServerSettings(server -> server.addServerListener(check));
  }

  /** Without this bean, a transactional method runs with no transaction and gives no warning. */
  @Bean
  MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
    return new CommitRetryingTransactionManager(databaseFactory);
  }
}

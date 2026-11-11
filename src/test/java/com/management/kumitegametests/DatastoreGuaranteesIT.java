package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.athlete.data.AthleteDocument;
import com.management.athlete.data.AthleteDocumentMapper;
import com.management.athlete.data.AthleteRepository;
import com.management.config.DatastoreProperties;
import com.management.testsupport.IntegrationTestBase;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoDatabase;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;

/** What the running connection really gives, read from the server and not from configuration. */
class DatastoreGuaranteesIT extends IntegrationTestBase {

  @Autowired private AthleteRepository athletes;
  @Autowired private DatastoreProperties properties;
  @Autowired private List<MongoClientSettingsBuilderCustomizer> customizers;

  @Test
  void server_isReplicaSetMember() {
    Document hello = mongoTemplate.executeCommand("{hello: 1}");

    assertThat(hello.getString("setName")).isNotBlank();
  }

  /**
   * The server's profiler records the write concern each write arrived with. A replica set already
   * defaults to majority, so reading the setting back, or the outcome of a write, could not tell
   * whether the application asked for it.
   */
  @Test
  void repositoryWrite_reachesTheServerWithMajorityWriteConcern() {
    MongoDatabase database = mongoTemplate.getDb();
    database.runCommand(new Document("profile", 2));
    try {
      AthleteDocument athlete = athletes.save(AthleteDocumentMapper.toNewDocument("Kenji", null));

      Document profiled =
          database
              .getCollection("system.profile")
              .find(new Document("op", "insert").append("ns", namespaceOf(database, athlete)))
              .first();

      assertThat(profiled).isNotNull();
      assertThat(profiled.get("command", Document.class).get("writeConcern", Document.class))
          .containsEntry("w", "majority");
    } finally {
      database.runCommand(new Document("profile", 0));
    }
  }

  @Test
  void connection_boundsEveryOperation() {
    assertThat(mongoTemplate.getDb().getTimeout(TimeUnit.MILLISECONDS))
        .isEqualTo(properties.operationTimeout().toMillis());
  }

  @Test
  void connection_boundsConnectingAndServerSelection() {
    MongoClientSettings settings = settingsFrom("mongodb://localhost");

    assertThat(settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS))
        .isEqualTo(properties.connectTimeout().toMillis());
    assertThat(settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS))
        .isEqualTo(properties.serverSelectionTimeout().toMillis());
  }

  @Test
  void weakerOptionsInTheConnectionString_areOverridden() {
    MongoClientSettings settings =
        settingsFrom("mongodb://localhost/?retryWrites=false&readPreference=secondary");

    assertThat(settings.getRetryWrites()).isTrue();
    assertThat(settings.getReadPreference()).isEqualTo(ReadPreference.primary());
  }

  /** Built through the application's customizers, because a client does not expose these. */
  private MongoClientSettings settingsFrom(String connectionString) {
    MongoClientSettings.Builder builder =
        MongoClientSettings.builder().applyConnectionString(new ConnectionString(connectionString));
    customizers.forEach(customizer -> customizer.customize(builder));
    return builder.build();
  }

  private String namespaceOf(MongoDatabase database, AthleteDocument athlete) {
    return database.getName() + "." + mongoTemplate.getCollectionName(athlete.getClass());
  }
}

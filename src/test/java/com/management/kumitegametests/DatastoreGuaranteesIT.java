package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.config.DatastoreProperties;
import com.management.models.Player;
import com.management.repositories.PlayerRepository;
import com.management.testsupport.IntegrationTestBase;
import com.management.testsupport.PlayerBuilder;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoDatabase;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;

/** What the running connection really gives, read from the server and not from configuration. */
class DatastoreGuaranteesIT extends IntegrationTestBase {

  @Autowired private PlayerRepository players;
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
      Player player = players.save(PlayerBuilder.newPlayer().build());

      Document profiled =
          database
              .getCollection("system.profile")
              .find(new Document("op", "insert").append("ns", namespaceOf(database, player)))
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

  /** Built through the application's customizers, because a client does not expose these. */
  @Test
  void connection_boundsConnectingAndServerSelection() {
    MongoClientSettings.Builder builder = MongoClientSettings.builder();
    customizers.forEach(customizer -> customizer.customize(builder));
    MongoClientSettings settings = builder.build();

    assertThat(settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS))
        .isEqualTo(properties.connectTimeout().toMillis());
    assertThat(settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS))
        .isEqualTo(properties.serverSelectionTimeout().toMillis());
  }

  private String namespaceOf(MongoDatabase database, Player player) {
    return database.getName() + "." + mongoTemplate.getCollectionName(player.getClass());
  }
}

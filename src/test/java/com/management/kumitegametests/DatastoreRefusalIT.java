package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.management.KumiteGameStarter;
import com.management.testsupport.IntegrationTestBase;
import com.mongodb.WriteConcern;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * The application refuses a reachable datastore weaker than it was built on.
 *
 * <p>Starts the application by hand rather than through the test framework: a context that fails to
 * start is the expected result here, and a cached one could not express it. It also adds no third
 * context to the suite.
 *
 * <p>The containers come from {@link IntegrationTestBase}, the one file that names their types.
 */
class DatastoreRefusalIT {

  private static final GenericContainer<?> STANDALONE = IntegrationTestBase.standaloneMongo();
  private static final GenericContainer<?> UNINITIATED = IntegrationTestBase.uninitiatedMongo();

  @BeforeAll
  static void startContainers() {
    STANDALONE.start();
    UNINITIATED.start();
  }

  @AfterAll
  static void stopContainers() {
    STANDALONE.stop();
    UNINITIATED.stop();
  }

  /** Both shapes matter: with {@code replicaSet}, a standalone otherwise looks unreachable. */
  @ParameterizedTest
  @ValueSource(strings = {"", "?replicaSet=rs0"})
  void startup_onStandalone_fails(String options) {
    String uri = uriOf(STANDALONE) + options;

    assertThatThrownBy(() -> start(uri, "5s")).hasMessageContaining("standalone server");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "?replicaSet=rs0"})
  void startup_onUninitiatedMember_fails(String options) {
    String uri = uriOf(UNINITIATED) + options;

    assertThatThrownBy(() -> start(uri, "1s")).hasMessageContaining("never initiated");
  }

  /** Also the control: a replica set starts through the same path the refusals take. */
  @Test
  void weakerWriteConcernInTheConnectionDetails_isOverridden() {
    String uri = IntegrationTestBase.replicaSetUri() + "?w=1";

    try (ConfigurableApplicationContext context = start(uri, "5s")) {
      assertThat(context.getBean(MongoTemplate.class).getDb().getWriteConcern())
          .isEqualTo(WriteConcern.MAJORITY);
    }
  }

  /** Pausing keeps the port open and silent, so the first answer really does come after startup. */
  @Test
  void runningApplication_stops_whenStandaloneFirstAnswersAfterStartup() {
    GenericContainer<?> late = IntegrationTestBase.standaloneMongo();
    late.start();
    try {
      late.getDockerClient().pauseContainerCmd(late.getContainerId()).exec();

      try (ConfigurableApplicationContext context = start(uriOf(late), "500ms")) {
        assertThat(context.isActive()).isTrue();

        late.getDockerClient().unpauseContainerCmd(late.getContainerId()).exec();

        await().atMost(Duration.ofSeconds(30)).until(() -> !context.isActive());
      }
    } finally {
      late.stop();
    }
  }

  private static ConfigurableApplicationContext start(String uri, String startupWait) {
    return new SpringApplicationBuilder(KumiteGameStarter.class)
        .profiles("test")
        .properties(
            "server.port=0",
            "spring.mongodb.uri=" + uri,
            "datastore.startup-wait=" + startupWait,
            "datastore.operation-timeout=2s")
        .run();
  }

  private static String uriOf(GenericContainer<?> container) {
    return "mongodb://%s:%d/kumitedb"
        .formatted(container.getHost(), container.getFirstMappedPort());
  }
}

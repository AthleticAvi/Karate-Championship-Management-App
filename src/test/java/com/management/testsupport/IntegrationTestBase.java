package com.management.testsupport;

import com.management.KumiteGameStarter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Base class for integration tests. One container, one context, clean data.
 *
 * <p><strong>One container for the whole suite.</strong> The container is a {@code static} field
 * started once in a static initialiser and never stopped by JUnit — the documented
 * singleton-container pattern. Testcontainers' resource reaper removes it when the JVM exits.
 * Declaring {@code @Container} instead would tie its lifecycle to a single test class, so the
 * second class to run would find a stopped container.
 *
 * <p><strong>One context configuration.</strong> Every integration test inherits this exact
 * annotation set, so the framework builds and caches a single context for all of them. Varying the
 * configuration per class — different properties, different web environment — multiplies context
 * builds and comes to dominate suite time. A random web port is used for every test, including
 * those that do not make HTTP calls, because one shared configuration is worth more than a
 * marginally cheaper context for some of them.
 *
 * <p><strong>Data isolation: clean before, not after.</strong> Every collection is dropped before
 * each test. Cleaning afterwards instead would depend on teardown actually running, and a test that
 * crashes or times out would leave state behind for the next one — reintroducing exactly the order
 * dependence this class exists to remove. Cleaning first also leaves the failing test's data in
 * place for inspection.
 *
 * <p><strong>This is the only file that names a Testcontainers MongoDB type.</strong> Keep it that
 * way. Testcontainers 2.x relocates container classes into module-specific packages, and Epic #89
 * will make that move; concentrating the reference here turns that migration into a one-line change
 * no matter how many integration tests exist by then.
 *
 * <p><strong>A replica set, as in production.</strong> A container is standalone unless asked, and
 * a standalone hides what production gives. The URL it hands over names no {@code replicaSet}, so
 * the tests connect directly where production discovers members: {@code [facts]} in the
 * repositories README records what that leaves untested.
 *
 * <p><strong>Active profile: test.</strong> The {@code @ActiveProfiles("test")} annotation
 * activates the test profile for all integration tests, ensuring test-specific configuration is
 * used while preserving a single shared context across the suite.
 */
@SpringBootTest(classes = KumiteGameStarter.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

  private static final String MONGO_IMAGE = "mongo:8.0";
  private static final int MONGO_PORT = 27017;

  @ServiceConnection
  static final MongoDBContainer MONGO = new MongoDBContainer(MONGO_IMAGE).withReplicaSet();

  static {
    MONGO.start();
  }

  @Autowired protected MongoTemplate mongoTemplate;

  /** The shared replica set, for the tests that start the application by hand. */
  public static String replicaSetUri() {
    return MONGO.getReplicaSetUrl();
  }

  /** A standalone server, weaker than the shared one, for the tests that must be refused. */
  public static GenericContainer<?> standaloneMongo() {
    return new MongoDBContainer(MONGO_IMAGE);
  }

  /** A replica set member nobody initiated, for the tests that must be refused. */
  public static GenericContainer<?> uninitiatedMongo() {
    return new GenericContainer<>(MONGO_IMAGE)
        .withCommand("--replSet", "rs0")
        .withExposedPorts(MONGO_PORT)
        .waitingFor(Wait.forLogMessage(".*Waiting for connections.*\\n", 1));
  }

  @BeforeEach
  void dropEveryCollection() {
    mongoTemplate.getCollectionNames().forEach(mongoTemplate::dropCollection);
  }
}

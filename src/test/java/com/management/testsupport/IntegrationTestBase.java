package com.management.testsupport;

import com.management.kumitegame.KumiteGameStarter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;

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
 * <p><strong>This is the only file that names a Testcontainers container type.</strong> Keep it
 * that way. Testcontainers 2.x relocates container classes into module-specific packages, and Epic
 * #89 will make that move; concentrating the reference here turns that migration into a one-line
 * change no matter how many integration tests exist by then.
 */
@SpringBootTest(classes = KumiteGameStarter.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

  @ServiceConnection static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

  static {
    MONGO.start();
  }

  @Autowired protected MongoTemplate mongoTemplate;

  @BeforeEach
  void dropEveryCollection() {
    mongoTemplate.getCollectionNames().forEach(mongoTemplate::dropCollection);
  }
}

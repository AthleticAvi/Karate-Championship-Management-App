package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.kumitegame.KumiteGameStarter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Health reports DOWN when MongoDB cannot be reached.
 *
 * <p><strong>Why this does not use a container.</strong> The scenario needs MongoDB to be
 * unavailable. The previous version produced that by calling {@code stop()} on the container the
 * suite shared, which left every later test running against a dead database and forced the class
 * into a fixed method order to hide it. Pointing this context at a port nothing listens on gives
 * the same assertion with no blast radius, no ordering dependence, and no container to start.
 *
 * <p>It is also faster and more honest: a refused connection is immediate and unambiguous, where
 * stopping a container leaves the driver retrying against a socket that is closing.
 *
 * <p>This class deliberately carries its own configuration rather than extending {@code
 * IntegrationTestBase} — a context that cannot reach the database is precisely what the shared one
 * must never be. It is the one justified exception to the single-configuration rule, so the suite
 * builds two contexts and no more.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(
    classes = KumiteGameStarter.class,
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      // Port 1 is reserved and nothing listens on it, so the connection is refused immediately.
      // serverSelectionTimeoutMS is cut from its 30s default: without it the driver spends half a
      // minute retrying before the health indicator gives up, and this test is the slowest in the
      // suite for no reason. 500ms is far longer than a local refused connection needs.
      "spring.mongodb.uri=mongodb://localhost:1/kumitedb?serverSelectionTimeoutMS=500"
    })
class ActuatorHealthDownIT {

  @Autowired private TestRestTemplate rest;

  @Test
  void health_whenMongoIsUnreachable_reportsDownWith503() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).contains("\"status\":\"DOWN\"");
  }
}

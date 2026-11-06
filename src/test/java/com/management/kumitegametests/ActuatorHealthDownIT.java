package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.KumiteGameStarter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

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
 * must never be. It is the one justified exception to the single-configuration rule, so the test
 * framework builds two contexts and no more.
 *
 * <p><strong>The exception is the context, not the profile.</strong> {@code @ActiveProfiles} is
 * repeated here because the annotation arrives by inheritance and this class has no base to inherit
 * it from. Without it this would be the only test in the suite running with no profile, and the
 * first property added to a test profile file would reach every test except this one.
 */
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SpringBootTest(
    classes = KumiteGameStarter.class,
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      // Port 1 is reserved and nothing listens on it, so the connection is refused immediately.
      "spring.mongodb.uri=mongodb://localhost:1/kumitedb",
      // Cut from the default so the health indicator gives up sooner, and this test is not the
      // slowest in the suite for no reason. 500ms is far longer than a refused connection needs.
      "datastore.server-selection-timeout=500ms",
      // Nothing will ever answer, so waiting the full startup default would only slow this test.
      "datastore.startup-wait=100ms"
    })
class ActuatorHealthDownIT {

  @Autowired private TestRestTemplate rest;

  @Test
  void readiness_whenMongoIsUnreachable_reportsDownWith503() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health/readiness", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).contains("\"status\":\"DOWN\"");
  }

  /** Liveness says the process runs: an outage must not get it restarted. */
  @Test
  void liveness_whenMongoIsUnreachable_staysUp() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health/liveness", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /** Nobody knows if these credentials are right, so a 401 would send the caller the wrong way. */
  @Test
  void credentials_whenMongoIsUnreachable_failAsServerFault() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBasicAuth("someone", "anything");

    ResponseEntity<String> response =
        rest.exchange(
            "/api/v1/players", HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getHeaders().getFirst("Content-Type"))
        .isEqualTo("application/problem+json");
  }
}

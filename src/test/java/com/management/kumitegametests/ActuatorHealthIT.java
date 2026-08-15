package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Health reports UP while MongoDB is reachable.
 *
 * <p>The unreachable case lives in {@link ActuatorHealthDownIT}. It cannot share this container:
 * asserting DOWN requires the database to be unavailable, and producing that by stopping shared
 * infrastructure would break every test that runs afterwards.
 *
 * <p>No {@code await(...)} here. The container is started before the context, so by the time a test
 * runs the connection either works or is genuinely broken — polling would only hide a real failure
 * behind a timeout.
 */
class ActuatorHealthIT extends IntegrationTestBase {

  @Autowired private TestRestTemplate rest;

  @Test
  void health_whenMongoIsReachable_reportsUpWithMongoDetail() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
    assertThat(response.getBody()).contains("\"mongo\"");
  }
}

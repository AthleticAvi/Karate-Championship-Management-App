package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

/**
 * Health reports UP while MongoDB is reachable, and who may read what.
 *
 * <p>Only liveness and readiness are public, and they carry nothing but a status. Everything else
 * the actuator serves needs an Admin.
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

  @ParameterizedTest
  @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
  void probes_arePublicAndCarryOnlyTheirStatus(String path) {
    ResponseEntity<String> response = rest.getForEntity(path, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/actuator", "/actuator/health", "/actuator/info", "/actuator/env"})
  void everythingElse_needsCredentials(String path) {
    assertThat(rest.getForEntity(path, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/actuator", "/actuator/health", "/actuator/info"})
  void everythingElse_isRefusedToScorekeeper(String path) {
    assertThat(asScorekeeper().getForEntity(path, String.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void admin_seesTheDetail() {
    ResponseEntity<String> response = asAdmin().getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"").contains("\"mongo\"");
  }

  /**
   * The probes are public, so the role on the detail is all that hides it from a signed-in caller.
   */
  @Test
  void readiness_showsItsDetailOnlyToAdmin() {
    String path = "/actuator/health/readiness";

    assertThat(asScorekeeper().getForEntity(path, String.class).getBody())
        .isEqualTo("{\"status\":\"UP\"}");
    assertThat(asAdmin().getForEntity(path, String.class).getBody()).contains("\"mongo\"");
  }

  @Test
  void admin_readsInfo() {
    assertThat(asAdmin().getForEntity("/actuator/info", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  /** The exposure list: what the index names is all there is, even for an Admin. */
  @Test
  void onlyHealthAndInfo_areExposed() {
    String index = asAdmin().getForEntity("/actuator", String.class).getBody();

    assertThat(JsonMapper.shared().readTree(index).get("_links").propertyNames())
        .containsExactlyInAnyOrder("self", "health", "health-path", "info");
    assertThat(asAdmin().getForEntity("/actuator/env", String.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }
}

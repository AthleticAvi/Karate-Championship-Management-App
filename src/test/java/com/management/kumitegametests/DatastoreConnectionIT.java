package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.testsupport.IntegrationTestBase;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * A connection URI carrying credentials stays out of the logs, and bad bounds stop startup.
 *
 * <p>Starts the application by hand, as {@code DatastoreRefusalIT} does: a failed connection is
 * what writes the driver's errors, and a failed start is the expected result of some cases here.
 */
@ExtendWith(OutputCaptureExtension.class)
class DatastoreConnectionIT {

  private static final String USER = "kumite";
  private static final String PASSWORD = "s3cret-Pa55word";
  private static final String URI = "mongodb://kumite:" + PASSWORD + "@localhost:1/kumitedb";

  @Test
  void password_staysOutOfTheLogs_whenTheServerIsUnreachable(CapturedOutput output) {
    try (ConfigurableApplicationContext context = start(URI, Map.of())) {
      MongoTemplate template = context.getBean(MongoTemplate.class);

      assertThatThrownBy(() -> template.executeCommand("{ping: 1}"))
          .satisfies(failure -> assertThat(failure.toString()).doesNotContain(PASSWORD));
    }

    assertThat(output.getAll())
        .contains("localhost:1")
        .doesNotContain(PASSWORD)
        .doesNotContain("userName");
  }

  /** The server answers and refuses the login, a path an unreachable port never runs. */
  @Test
  void password_staysOutOfLogsAndHealth_whenTheServerRejectsIt(CapturedOutput output) {
    GenericContainer<?> mongo = IntegrationTestBase.authenticatedMongo(USER, "the-real-password");
    try {
      String uri =
          "mongodb://%s:%s@%s:%d/kumitedb?authSource=admin&directConnection=true"
              .formatted(USER, PASSWORD, mongo.getHost(), mongo.getFirstMappedPort());

      try (ConfigurableApplicationContext context = start(uri, Map.of())) {
        MongoTemplate template = context.getBean(MongoTemplate.class);
        assertThatThrownBy(() -> template.executeCommand("{ping: 1}"))
            .satisfies(failure -> assertThat(failure.toString()).doesNotContain(PASSWORD));

        ResponseEntity<String> health = health(context);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(health.getBody()).doesNotContain(PASSWORD).doesNotContain(USER);
      }
    } finally {
      mongo.stop();
    }

    assertThat(output.getAll()).doesNotContain(PASSWORD);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "kumite:" + PASSWORD + "@localhost:1/kumitedb",
        "mongodb://kumite:" + PASSWORD + "@localhost:notaport/kumitedb",
        "mongodb://kumite:" + PASSWORD + "@with-an-at-sign@localhost:1/kumitedb"
      })
  void password_staysOutOfTheLogs_whenTheUriIsMalformed(String malformed, CapturedOutput output) {
    assertThatThrownBy(() -> start(malformed, Map.of()))
        .satisfies(failure -> assertThat(failure.toString()).doesNotContain(PASSWORD));

    assertThat(output.getAll()).doesNotContain(PASSWORD);
  }

  /** Zero means no limit to the driver, or for server selection failing at once. */
  @ParameterizedTest
  @ValueSource(strings = {"connect-timeout", "server-selection-timeout"})
  void startup_withZeroBound_fails(String key) {
    assertThatThrownBy(() -> start(URI, Map.of("datastore." + key, "0s")))
        .hasRootCauseInstanceOf(BindValidationException.class);
  }

  private static ResponseEntity<String> health(ConfigurableApplicationContext context) {
    String port = context.getEnvironment().getProperty("local.server.port");
    return RestClient.create()
        .get()
        .uri("http://localhost:" + port + "/actuator/health")
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  private static ConfigurableApplicationContext start(String uri, Map<String, String> overrides) {
    Map<String, String> settings = new HashMap<>();
    settings.put("datastore.startup-wait", "100ms");
    settings.put("datastore.server-selection-timeout", "500ms");
    settings.put("datastore.operation-timeout", "2s");
    settings.putAll(overrides);
    return IntegrationTestBase.startApplication(uri, settings);
  }
}

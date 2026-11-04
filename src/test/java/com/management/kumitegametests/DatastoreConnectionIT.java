package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.KumiteGameStarter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * A connection URI carrying credentials stays out of the logs, and bad bounds stop startup.
 *
 * <p>Starts the application by hand, as {@code DatastoreRefusalIT} does, against a port nothing
 * listens on: a failed connection is what writes the driver's errors, and no container is needed.
 */
@ExtendWith(OutputCaptureExtension.class)
class DatastoreConnectionIT {

  private static final String PASSWORD = "s3cret-Pa55word";
  private static final String URI = "mongodb://kumite:" + PASSWORD + "@localhost:1/kumitedb";

  @Test
  void credentials_stayOutOfTheLogs_throughStartupAndFailedOperation(CapturedOutput output) {
    try (ConfigurableApplicationContext context = start(URI)) {
      MongoTemplate template = context.getBean(MongoTemplate.class);

      assertThatThrownBy(() -> template.executeCommand("{ping: 1}"))
          .satisfies(failure -> assertThat(failure.toString()).doesNotContain(PASSWORD));
    }

    assertThat(output.getAll())
        .contains("localhost:1")
        .doesNotContain(PASSWORD)
        .doesNotContain(URI);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "kumite:" + PASSWORD + "@localhost:1/kumitedb",
        "mongodb://kumite:" + PASSWORD + "@localhost:notaport/kumitedb",
        "mongodb://kumite:" + PASSWORD + "@with-an-at-sign@localhost:1/kumitedb"
      })
  void credentials_stayOutOfTheLogs_whenTheUriIsMalformed(String malformed, CapturedOutput output) {
    assertThatThrownBy(() -> start(malformed))
        .satisfies(failure -> assertThat(failure.toString()).doesNotContain(PASSWORD));

    assertThat(output.getAll()).doesNotContain(PASSWORD);
  }

  /** The driver reads zero as no limit at all, so it must never reach the driver. */
  @ParameterizedTest
  @ValueSource(strings = {"connect-timeout", "server-selection-timeout"})
  void startup_withZeroBound_fails(String key) {
    assertThatThrownBy(() -> start(URI, Map.of("datastore." + key, "0s")))
        .hasRootCauseInstanceOf(BindValidationException.class);
  }

  private static ConfigurableApplicationContext start(String uri) {
    return start(uri, Map.of());
  }

  /** Arguments, because the builder's own properties rank below application.properties. */
  private static ConfigurableApplicationContext start(String uri, Map<String, String> overrides) {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("server.port", "0");
    settings.put("spring.mongodb.uri", uri);
    settings.put("datastore.startup-wait", "100ms");
    settings.put("datastore.server-selection-timeout", "200ms");
    settings.put("datastore.operation-timeout", "1s");
    settings.putAll(overrides);
    String[] args =
        settings.entrySet().stream()
            .map(setting -> "--" + setting.getKey() + "=" + setting.getValue())
            .toArray(String[]::new);
    return new SpringApplicationBuilder(KumiteGameStarter.class).profiles("test").run(args);
  }
}

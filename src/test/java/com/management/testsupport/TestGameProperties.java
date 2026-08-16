package com.management.testsupport;

import com.management.config.GameProperties;
import java.time.Duration;
import java.util.List;

/**
 * The standard duration configuration for tests: the same values {@code application.properties}
 * ships, stated once. A test about durations builds its own {@link GameProperties} instead — the
 * record is plain data, which is the whole point of #42.
 */
public final class TestGameProperties {

  private TestGameProperties() {}

  public static GameProperties standard() {
    return new GameProperties(
        Duration.ofSeconds(120), List.of(Duration.ofSeconds(90), Duration.ofSeconds(180)));
  }
}

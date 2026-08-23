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

  public static final int WINNING_POINTS = 8;
  public static final int FOULS_ENDING_MATCH = 4;

  public static GameProperties standard() {
    return new GameProperties(
        Duration.ofSeconds(120),
        List.of(Duration.ofSeconds(90), Duration.ofSeconds(180)),
        WINNING_POINTS,
        FOULS_ENDING_MATCH,
        List.of(Duration.ofSeconds(10), Duration.ofSeconds(30)));
  }
}

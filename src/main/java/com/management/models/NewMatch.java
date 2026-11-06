package com.management.models;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A match to create, as the business layer sees it: the gateway maps the validated request into
 * this, so services never depend on an API shape.
 *
 * <p>The colour stays text here because whether it names a real colour is a business decision, with
 * its own failure.
 *
 * @param gameDuration seconds, or {@code null} for the configured default
 */
public record NewMatch(
    List<Fighter> fighters, List<String> referees, @Nullable Integer gameDuration) {

  public NewMatch {
    fighters = List.copyOf(fighters);
    referees = List.copyOf(referees);
  }

  /** A fighter to create for the match. */
  public record Fighter(String name, String color) {}
}

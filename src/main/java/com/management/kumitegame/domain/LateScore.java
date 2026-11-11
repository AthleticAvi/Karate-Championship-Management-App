package com.management.kumitegame.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Warns on a score recorded in TIME_UP later than the late-score window after the clock reached
 * zero.
 *
 * <p>A score landing as time runs out is valid (Article 8.10), but the server cannot see when the
 * technique landed, only when the zero was reported. So a late score is recorded as given, and
 * warns.
 */
final class LateScore {

  private LateScore() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  static void check(GameState state, Instant at, Settings settings, List<Warning> raised) {
    Instant timeUpAt = state.timeUpAt();
    if (state.status() == GameStatus.TIME_UP
        && timeUpAt != null
        && Duration.between(timeUpAt, at).toMillis() > settings.lateScoreWindowMs()) {
      raised.add(Warning.SCORE_AFTER_LATE_SCORE_WINDOW);
    }
  }
}

package com.management.kumitegame.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Warns when the time a screen showed is further from the derived clock than the settings allow.
 *
 * <p>The screen's time is the fact: it is what the scorekeeper reacted to, and network delay moves
 * the server's instant by an unknown amount. Neither value is replaced.
 */
final class ClockTolerance {

  private ClockTolerance() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  /** For a clock action: what the screen showed against the timer it pressed. */
  static void checkTimeShown(
      ClockState clock, long timeShownMs, Instant at, Settings settings, List<Warning> raised) {
    if (isOutside(clock, timeShownMs, at, settings)) {
      raised.add(Warning.TIME_SHOWN_OUT_OF_TOLERANCE);
    }
  }

  /** For a score or penalty: the clock remaining it was sent with, against the main clock. */
  static void checkClockRemaining(
      ClockState mainClock,
      @Nullable Long clockRemainingMs,
      Instant at,
      Settings settings,
      List<Warning> raised) {
    if (clockRemainingMs != null && isOutside(mainClock, clockRemainingMs, at, settings)) {
      raised.add(Warning.CLOCK_REMAINING_OUT_OF_TOLERANCE);
    }
  }

  private static boolean isOutside(ClockState clock, long shownMs, Instant at, Settings settings) {
    return Math.abs(shownMs - clock.remainingAt(at)) > settings.timeShownToleranceMs();
  }
}

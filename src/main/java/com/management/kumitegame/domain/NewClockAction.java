package com.management.kumitegame.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A clock action as the client sent it. */
public record NewClockAction(
    String id,
    Timer timer,
    @Nullable Side side,
    ClockActionKind action,
    long timeShownMs,
    @Nullable Long amountMs,
    @Nullable PauseReason pauseReason) {

  /** The same request: only what the client sent, and who sent it, are compared. */
  boolean matches(ClockAction stored, String recordedBy) {
    return timer == stored.timer()
        && side == stored.side()
        && action == stored.action()
        && timeShownMs == stored.timeShownMs()
        && Objects.equals(amountMs, stored.amountMs())
        && pauseReason == stored.pauseReason()
        && recordedBy.equals(stored.meta().recordedBy());
  }
}

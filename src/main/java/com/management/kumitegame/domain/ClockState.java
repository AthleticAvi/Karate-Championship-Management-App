package com.management.kumitegame.domain;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A clock, derived from its actions: the server never ticks.
 *
 * <p>{@code remainingMs} is what was left at {@code since}. While running, the time left at any
 * later instant is that minus the time passed, and never below zero.
 */
public record ClockState(boolean running, long remainingMs, @Nullable Instant since) {

  static ClockState stopped(long remainingMs) {
    return new ClockState(false, remainingMs, null);
  }

  /** The time left at {@code at}. */
  public long remainingAt(Instant at) {
    if (!running || since == null) {
      return remainingMs;
    }
    long passed = Math.max(0, Duration.between(since, at).toMillis());
    return Math.max(0, remainingMs - passed);
  }

  /**
   * The clock after an action, per the clock table: only ADD_TIME keeps the run state, and only
   * START and ADD_TIME keep the time left. A second START or PAUSE changes nothing but the instant.
   *
   * @param amountMs the time added or set; zero for an action that takes none
   * @param startingMs what RESTART puts back: the game's duration, or the medical time
   */
  ClockState after(
      ClockActionKind action, Instant at, long timeShownMs, long amountMs, long startingMs) {
    long left = remainingAt(at);
    return switch (action) {
      case START -> new ClockState(true, left, at);
      case PAUSE -> new ClockState(false, running ? timeShownMs : left, at);
      case RESTART -> new ClockState(false, startingMs, at);
      case ADD_TIME -> new ClockState(running, left + amountMs, at);
      case SET_TIME -> new ClockState(false, amountMs, at);
      case TIME_UP -> new ClockState(false, 0, at);
    };
  }
}

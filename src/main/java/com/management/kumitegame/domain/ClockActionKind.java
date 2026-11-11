package com.management.kumitegame.domain;

/** What a clock action did. The effect of each is in {@code ClockState}. */
public enum ClockActionKind {
  START,
  PAUSE,
  RESTART,
  ADD_TIME,
  SET_TIME,
  TIME_UP;

  /** Needs an amount: the time added, or the time set. */
  public boolean hasAmount() {
    return this == ADD_TIME || this == SET_TIME;
  }

  /** On the main clock, puts time back after it reached zero. */
  public boolean isPuttingTimeBack() {
    return this == ADD_TIME || this == RESTART || this == SET_TIME;
  }
}

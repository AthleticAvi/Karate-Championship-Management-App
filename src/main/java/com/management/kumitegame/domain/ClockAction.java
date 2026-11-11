package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * A press on the main clock or a side's medical timer.
 *
 * @param side the medical timer's side; empty for the main clock
 * @param timeShownMs what the screen showed when the button was pressed
 * @param amountMs the time added or set; only for ADD_TIME and SET_TIME
 */
public record ClockAction(
    Timer timer,
    @Nullable Side side,
    ClockActionKind action,
    long timeShownMs,
    @Nullable Long amountMs,
    @Nullable PauseReason pauseReason,
    EntryMeta meta)
    implements Entry {}

package com.management.kumitegame.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

/**
 * A referee's request to put time back on the clock.
 *
 * <p>Seconds as an integer, so a non-numeric value dies at binding rather than in a hand parse.
 * Which values are actually permitted is configuration — {@code game.clock-increments} — and is
 * checked in the service, because a constraint cannot see it.
 *
 * <p>Who added it comes from the signed-in account, so a caller cannot supply that.
 *
 * @param seconds how much time to add; must be one of the configured increments
 */
public record ClockAdjustmentRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive(message = "time added must be a positive number of seconds")
        int seconds) {}

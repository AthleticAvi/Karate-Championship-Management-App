package com.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A referee's request to put time back on the clock.
 *
 * <p>Seconds as an integer, so a non-numeric value dies at binding rather than in a hand parse.
 * Which values are actually permitted is configuration — {@code game.clock-increments} — and is
 * checked in the service, because a constraint cannot see it.
 *
 * @param seconds how much time to add; must be one of the configured increments
 * @param addedBy who added it. Caller-supplied until the security epic (#81) supplies an
 *     authenticated identity.
 */
public record ClockAdjustmentRequest(
    @Positive(message = "time added must be a positive number of seconds") int seconds,
    @NotBlank(message = "a clock adjustment must record who made it") @Size(max = 100)
        String addedBy) {}

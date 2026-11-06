package com.management.models;

import java.time.Duration;
import java.time.Instant;

/**
 * Time a referee added to the clock, and who added it.
 *
 * <p>Extending a match changes what can happen in it, so each addition is recorded rather than
 * silently folded into the remaining time.
 *
 * @param added how much time was added
 * @param addedAt when it was added
 * @param addedBy the signed-in account that added it. On an adjustment stored before accounts
 *     existed, a name the caller supplied
 */
public record ClockAdjustment(Duration added, Instant addedAt, String addedBy) {}

package com.management.models;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Time a referee added to the clock, and who added it.
 *
 * <p>Extending a match changes what can happen in it, so each addition is recorded rather than
 * silently folded into the remaining time. The actor is a caller-supplied name today; the security
 * epic (#81) replaces it with an authenticated identity.
 *
 * @param added how much time was added
 * @param addedAt when it was added
 * @param addedBy who added it
 */
public record ClockAdjustment(Duration added, LocalDateTime addedAt, String addedBy) {}

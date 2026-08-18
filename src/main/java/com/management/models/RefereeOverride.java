package com.management.models;

import com.management.enums.PlayerColor;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A referee's decision to set the result themselves, and the record of why.
 *
 * <p>An override is an exception to the rules applied by a human in a contested setting, so the
 * reason is the decision's justification and is required. The outcome the rules engine had reached
 * is kept alongside it: replacing it would destroy the only evidence of what the system thought,
 * which is exactly what a dispute asks about.
 *
 * @param winner the colour the referee declared, or {@code null} for a declared draw
 * @param reason why the referee overrode the result; never blank
 * @param decidedBy who decided — a name supplied by the caller today, replaced by an authenticated
 *     identity when the security epic lands (#81)
 * @param decidedAt when the override was applied
 * @param supersededOutcome what the rules engine had determined, or {@code null} if it had not
 *     decided anything yet
 */
public record RefereeOverride(
    @Nullable PlayerColor winner,
    String reason,
    String decidedBy,
    LocalDateTime decidedAt,
    @Nullable MatchOutcome supersededOutcome) {}

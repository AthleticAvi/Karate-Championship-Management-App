package com.management.models;

import com.management.enums.MatchEndReason;
import com.management.enums.PlayerColor;
import org.jspecify.annotations.Nullable;

/**
 * The result of a match: who won, why the match ended, and which rule decided it.
 *
 * <p>A colour and an enum, never a sentence — the client renders the wording. {@code winner} is
 * {@code null} for a genuine draw, which is a real outcome when the clock expires with level scores
 * and neither fighter has SENSHU.
 *
 * @param winner the winning colour, or {@code null} for a draw
 * @param reason why the match ended
 * @param decidedBy the name of the rule that produced this winner, for audit and display
 */
public record MatchOutcome(@Nullable PlayerColor winner, MatchEndReason reason, String decidedBy) {

  public static MatchOutcome draw(MatchEndReason reason, String decidedBy) {
    return new MatchOutcome(null, reason, decidedBy);
  }
}

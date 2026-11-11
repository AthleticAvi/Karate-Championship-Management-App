package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * The result the rules point to. The system suggests; the scorekeeper confirms by recording a
 * result.
 *
 * <p>A HANTEI is a WIN with no winner: the officials' vote names it, so the rules never do (Article
 * 12.2.4).
 *
 * @param winner for a WIN, unless the officials decide it by HANTEI
 * @param bothDisqualified both sides disqualified, as in a medal game decided on points (Article
 *     12.2.11)
 */
public record SuggestedResult(
    Outcome outcome, @Nullable Side winner, ResultReason reason, boolean bothDisqualified) {

  public static SuggestedResult win(Side winner, ResultReason reason) {
    return new SuggestedResult(Outcome.WIN, winner, reason, false);
  }

  /** A WIN the officials decide by vote: no winner is suggested. */
  public static SuggestedResult hantei() {
    return new SuggestedResult(Outcome.WIN, null, ResultReason.HANTEI, false);
  }

  /** The same suggestion, with both sides disqualified. */
  public SuggestedResult withBothDisqualified() {
    return new SuggestedResult(outcome, winner, reason, true);
  }

  /**
   * Whether a result agrees with what the rules suggest: the same outcome, reason and
   * disqualification, and the same winner unless the officials decide it by HANTEI. The detail is
   * not compared. With no suggestion, only KIKEN and SHIKKAKU agree: an athlete absent, or
   * disqualified for conduct the scorekeeper may record as the result alone, ends a game whatever
   * the score (Articles 6, 10.4.17). Nothing else ends a game the rules do not point to an end of.
   */
  static boolean agrees(@Nullable SuggestedResult suggested, Result result) {
    return agrees(
        suggested, result.outcome(), result.winner(), result.reason(), result.bothDisqualified());
  }

  static boolean agrees(@Nullable SuggestedResult suggested, NewResult result) {
    return agrees(
        suggested, result.outcome(), result.winner(), result.reason(), result.bothDisqualified());
  }

  private static boolean agrees(
      @Nullable SuggestedResult suggested,
      Outcome outcome,
      @Nullable Side winner,
      ResultReason reason,
      boolean bothDisqualified) {
    if (suggested == null) {
      return reason.isBeforeTheStart();
    }
    return suggested.outcome == outcome
        && suggested.reason == reason
        && suggested.bothDisqualified == bothDisqualified
        && (suggested.winner == null || suggested.winner == winner);
  }
}

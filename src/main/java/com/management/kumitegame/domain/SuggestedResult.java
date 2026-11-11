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
   * not compared, and a DOUBLE_DISQUALIFICATION means both sides are disqualified, flag or not.
   *
   * <p>KIKEN always agrees: the rules never suggest it, and an athlete absent or withdrawn loses
   * whatever the score (Articles 6, 12.2.1). With no suggestion, SHIKKAKU agrees too: it may be
   * recorded as the result alone, before the game or after it (Article 10.4.17). Nothing else ends
   * a game the rules do not point to an end of.
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
    if (reason == ResultReason.KIKEN) {
      return true;
    }
    if (suggested == null) {
      return reason == ResultReason.SHIKKAKU;
    }
    return suggested.outcome == outcome
        && suggested.reason == reason
        && suggested.isDoubleDisqualification()
            == (bothDisqualified || reason == ResultReason.DOUBLE_DISQUALIFICATION)
        && (suggested.winner == null || suggested.winner == winner);
  }

  private boolean isDoubleDisqualification() {
    return bothDisqualified || reason == ResultReason.DOUBLE_DISQUALIFICATION;
  }
}

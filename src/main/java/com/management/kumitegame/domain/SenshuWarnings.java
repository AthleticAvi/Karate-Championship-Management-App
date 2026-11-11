package com.management.kumitegame.domain;

import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Warns on SENSHU breaches the system can see (Article 12.2). Never refuses: officials decide who
 * holds SENSHU and when it is withdrawn, and the scorekeeper records it as given.
 */
final class SenshuWarnings {

  private SenshuWarnings() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  /**
   * On an AWARDED: the other side scored in the same exchange, so the score was not unopposed
   * (Article 12.2.2), or SENSHU was closed by a withdrawal in the final window (Article 12.2.10).
   *
   * @param exchange the exchange the award belongs to; empty before the start
   */
  static void checkAward(
      GameState state,
      @Nullable Integer exchange,
      List<Score> opponentScores,
      List<Warning> raised) {
    if (exchange != null
        && opponentScores.stream()
            .anyMatch(
                score -> exchange.equals(score.exchange()) && !state.isCancelled(score.id()))) {
      raised.add(Warning.SENSHU_WHEN_BOTH_SCORED);
    }
    if (state.senshu().closed()) {
      raised.add(Warning.SENSHU_AFTER_CLOSED);
    }
  }

  /** On a WITHDRAWN: the side does not hold SENSHU, so there is nothing to withdraw. */
  static void checkWithdrawal(GameState state, Side side, List<Warning> raised) {
    if (!state.senshu().isHeldBy(side)) {
      raised.add(Warning.SENSHU_WITHDRAWN_NOT_HELD);
    }
  }

  /**
   * On a score: the holder's opponent scored in the exchange SENSHU was awarded for, so that award
   * was not for an unopposed score (Articles 12.2.2, 12.2.9). A video review adding the opponent's
   * score after the award is the usual way this happens.
   *
   * @param exchange the exchange the score belongs to; empty before the start
   * @param award the AWARDED the holder holds SENSHU by
   */
  static void checkScore(
      Side scorer, @Nullable Integer exchange, @Nullable SenshuRecord award, List<Warning> raised) {
    if (exchange != null
        && award != null
        && award.side() == scorer.opponent()
        && exchange.equals(award.exchange())) {
      raised.add(Warning.SENSHU_WHEN_BOTH_SCORED);
    }
  }

  /**
   * On a penalty: the holder warned for avoiding combat in the final window forfeits SENSHU
   * (Articles 12.2.8, 10.4.16), so the officials should withdraw it. Only a penalty that names an
   * avoiding-combat infraction is checked, whatever its level: which level forfeits is the
   * referee's call.
   */
  static void checkPenalty(
      GameState state,
      Side offender,
      @Nullable Infraction infraction,
      boolean inFinalWindow,
      List<Warning> raised) {
    if (infraction != null
        && infraction.isAvoidingCombat()
        && inFinalWindow
        && state.senshu().isHeldBy(offender)) {
      raised.add(Warning.SENSHU_HOLDER_AVOIDING_COMBAT);
    }
  }

  /**
   * On a CANCEL of the holder's score: the holder is left with no unopposed score, one in an
   * exchange where the opponent scored nothing (Article 12.2.2). SENSHU itself is unchanged:
   * officials withdraw it. A score before the start belongs to no exchange, so it is never
   * unopposed.
   *
   * @param standing whether a score still counts once this CANCEL applies
   */
  static void checkCancelledScore(
      List<Score> holderScores,
      List<Score> opponentScores,
      Predicate<String> standing,
      List<Warning> raised) {
    boolean unopposed =
        holderScores.stream()
            .filter(score -> standing.test(score.id()))
            .map(Score::exchange)
            .anyMatch(exchange ->
                exchange != null
                    && opponentScores.stream()
                        .filter(other -> standing.test(other.id()))
                        .noneMatch(other -> exchange.equals(other.exchange())));
    if (!unopposed) {
      raised.add(Warning.SENSHU_HOLDER_WITHOUT_UNOPPOSED_SCORE);
    }
  }
}

package com.management.kumitegame.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Each side's score as the game's format counts it, derived and never stored as score entries.
 *
 * <p>In round robin, a game won by HANSOKU, KIKEN or SHIKKAKU counts the offender's score as zero,
 * and the winner's as the higher of the forfeit points setting and what it scored, the points added
 * counted as YUKO. A double disqualification counts both as zero (WKF Kumite Competition Rules
 * 2026.01, Articles 10.7.1, 6.2, 12.3.2). Read from the result that stands, as the officials
 * decided it: a HANSOKU with no result yet leaves the score as scored. In elimination a penalty
 * gives the opponent nothing, so each side keeps what it scored.
 */
final class EffectiveScores {

  private static final Set<ResultReason> FORFEITS =
      Set.of(ResultReason.HANSOKU, ResultReason.KIKEN, ResultReason.SHIKKAKU);

  private EffectiveScores() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  static Map<Side, EffectiveScore> of(
      GameFormat format, Map<Side, SideState> sides, @Nullable Result result, Settings settings) {
    Map<Side, EffectiveScore> scores = new EnumMap<>(Side.class);
    sides.forEach((side, state) -> scores.put(side, EffectiveScore.earned(state)));
    if (format != GameFormat.ROUND_ROBIN || result == null) {
      return scores;
    }
    if (result.bothDisqualified() || result.reason() == ResultReason.DOUBLE_DISQUALIFICATION) {
      scores.replaceAll((side, score) -> EffectiveScore.zero());
      return scores;
    }
    Side winner = result.winner();
    EffectiveScore earned = winner == null ? null : scores.get(winner);
    if (winner == null || earned == null || !FORFEITS.contains(result.reason())) {
      return scores;
    }
    int added = Math.max(0, settings.roundRobinForfeitPoints() - earned.total());
    scores.put(
        winner,
        new EffectiveScore(
            earned.total() + added, earned.ipponCount(), earned.wazaAriCount(), added));
    scores.put(winner.opponent(), EffectiveScore.zero());
    return scores;
  }
}

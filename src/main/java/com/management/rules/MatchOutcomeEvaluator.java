package com.management.rules;

import com.management.enums.GameState;
import com.management.enums.MatchEndReason;
import com.management.models.GameWithFighters;
import com.management.models.MatchOutcome;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The rules engine: decides whether a match is over, and if so who won.
 *
 * <p>This is the component referred to elsewhere as the Game Orchestrator. It holds no state and
 * writes nothing — it answers a question, and {@code KumiteGameService} applies and persists the
 * answer. Keeping the decision separate from the write is what lets it be exercised as a plain unit
 * test with no container and no database, and what keeps ordering and persistence decided in one
 * place rather than in each rule.
 *
 * <p><strong>Both lists are injected, not constructed.</strong> The container supplies every {@link
 * MatchEndCondition} and every {@link WinnerRule} it finds, the latter sorted by {@code @Order}.
 * Adding a rule is adding a class; no existing file changes, which is the extensibility constraint
 * epic #68 sets out.
 */
@Component
public class MatchOutcomeEvaluator {

  private static final Logger log = LoggerFactory.getLogger(MatchOutcomeEvaluator.class);

  private final List<MatchEndCondition> endConditions;
  private final List<WinnerRule> winnerRules;

  public MatchOutcomeEvaluator(
      List<MatchEndCondition> endConditions, List<WinnerRule> winnerRules) {
    this.endConditions = List.copyOf(endConditions);
    this.winnerRules = List.copyOf(winnerRules);
  }

  /**
   * The outcome this match has reached, or empty if it is still being fought.
   *
   * <p>A match that has already finished, or that a referee has decided themselves, returns empty:
   * the first answer stands, and re-evaluating must never rewrite a recorded result.
   */
  public Optional<MatchOutcome> evaluate(GameWithFighters match) {
    if (match.game().getGameState() == GameState.FINISHED || match.game().isDecidedByReferee()) {
      return Optional.empty();
    }
    return firstTriggeredCondition(match).map(reason -> decideWinner(match, reason));
  }

  /**
   * Who won, given that the match is over for the stated reason.
   *
   * <p>Separate from the ending decision because the two are asked separately: a referee ending a
   * match by KIKEN knows the reason and needs only the winner.
   */
  public MatchOutcome decideWinner(GameWithFighters match, MatchEndReason reason) {
    return winnerRules.stream()
        .map(rule -> rule.decide(match, reason))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseGet(() -> MatchOutcome.draw(reason, "NoRuleApplied"));
  }

  private Optional<MatchEndReason> firstTriggeredCondition(GameWithFighters match) {
    for (MatchEndCondition condition : endConditions) {
      Optional<MatchEndReason> reason = condition.evaluate(match);
      if (reason.isPresent()) {
        log.debug(
            "Match {} ends: {} ({})",
            match.game().getId(),
            reason.get(),
            condition.getClass().getSimpleName());
        return reason;
      }
    }
    return Optional.empty();
  }
}

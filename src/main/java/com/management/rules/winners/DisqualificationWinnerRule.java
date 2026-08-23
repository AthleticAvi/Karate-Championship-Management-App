package com.management.rules.winners;

import com.management.enums.MatchEndReason;
import com.management.enums.PlayerColor;
import com.management.models.GameWithFighters;
import com.management.models.MatchOutcome;
import com.management.rules.WinnerRule;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A disqualified fighter loses, whatever the score said.
 *
 * <p>Highest priority: a fighter who is out cannot win on points they had already scored, so this
 * has to answer before any score-based rule gets a chance to.
 */
@Component
@Order(10)
public class DisqualificationWinnerRule implements WinnerRule {

  @Override
  public Optional<MatchOutcome> decide(GameWithFighters match, MatchEndReason reason) {
    Map<PlayerColor, com.management.models.Player> fighters = match.fighters();
    return fighters.entrySet().stream()
        .filter(entry -> entry.getValue().isDisqualified())
        .findFirst()
        .map(entry -> new MatchOutcome(opponentOf(entry.getKey()), reason, ruleName()));
  }

  private static PlayerColor opponentOf(PlayerColor color) {
    return color == PlayerColor.RED ? PlayerColor.BLUE : PlayerColor.RED;
  }

  private static String ruleName() {
    return DisqualificationWinnerRule.class.getSimpleName();
  }
}

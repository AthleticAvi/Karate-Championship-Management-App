package com.management.rules.winners;

import com.management.config.GameProperties;
import com.management.enums.MatchEndReason;
import com.management.enums.PlayerColor;
import com.management.models.GameWithFighters;
import com.management.models.MatchOutcome;
import com.management.models.Player;
import com.management.rules.WinnerRule;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A fighter who accumulates the foul limit loses the match.
 *
 * <p>Without this rule the score decided a foul-limit ending, which let a fighter <em>win by
 * fouling</em>: leading 3–0 and one foul short of the limit, committing that foul ended the match
 * with them still ahead. A penalty that benefits the fighter it penalises is not a penalty, and it
 * rewards exactly the behaviour the foul count exists to suppress.
 *
 * <p>Ranked below disqualification and above the score, mirroring the ending conditions: being put
 * out is more specific than fouling out, and both outrank whatever the scoreboard says.
 */
@Component
@Order(15)
public class FoulLimitWinnerRule implements WinnerRule {

  private final GameProperties gameProperties;

  public FoulLimitWinnerRule(GameProperties gameProperties) {
    this.gameProperties = gameProperties;
  }

  @Override
  public Optional<MatchOutcome> decide(GameWithFighters match, MatchEndReason reason) {
    Map<PlayerColor, Player> fighters = match.fighters();
    return fighters.entrySet().stream()
        .filter(entry -> hasFouledOut(entry.getValue()))
        .findFirst()
        .map(
            entry ->
                new MatchOutcome(
                    opponentOf(entry.getKey()), reason, FoulLimitWinnerRule.class.getSimpleName()));
  }

  private boolean hasFouledOut(Player fighter) {
    return fighter.getFouls().getNumOfFouls() >= gameProperties.foulsEndingMatch();
  }

  private static PlayerColor opponentOf(PlayerColor color) {
    return color == PlayerColor.RED ? PlayerColor.BLUE : PlayerColor.RED;
  }
}

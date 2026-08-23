package com.management.rules.winners;

import com.management.enums.MatchEndReason;
import com.management.enums.PlayerColor;
import com.management.models.GameWithFighters;
import com.management.models.MatchOutcome;
import com.management.models.Player;
import com.management.rules.WinnerRule;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The higher score wins.
 *
 * <p>Covers both the threshold crossing and an ordinary decision at time expiry: whoever crossed
 * the winning score necessarily has the higher score, so one rule serves both and there is no
 * second place for the comparison to drift. Level scores are not this rule's business — it declines
 * and SENSHU answers next.
 */
@Component
@Order(20)
public class HighestScoreWinnerRule implements WinnerRule {

  @Override
  public Optional<MatchOutcome> decide(GameWithFighters match, MatchEndReason reason) {
    int red = pointsOf(match, PlayerColor.RED);
    int blue = pointsOf(match, PlayerColor.BLUE);

    if (red == blue) {
      return Optional.empty();
    }
    PlayerColor leader = red > blue ? PlayerColor.RED : PlayerColor.BLUE;
    return Optional.of(
        new MatchOutcome(leader, reason, HighestScoreWinnerRule.class.getSimpleName()));
  }

  private static int pointsOf(GameWithFighters match, PlayerColor color) {
    Player fighter = match.fighters().get(color);
    return fighter == null ? 0 : fighter.getPoints().getNumOfPoints();
  }
}

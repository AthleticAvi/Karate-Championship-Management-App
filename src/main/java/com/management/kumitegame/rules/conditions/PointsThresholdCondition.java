package com.management.kumitegame.rules.conditions;

import com.management.athlete.domain.Player;
import com.management.config.GameProperties;
import com.management.kumitegame.domain.GameWithFighters;
import com.management.kumitegame.domain.MatchEndReason;
import com.management.kumitegame.rules.MatchEndCondition;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A fighter crossed the winning score.
 *
 * <p>Crossing, not reaching exactly: a fighter on 7 who lands an IPPON finishes on 10, and the
 * score is never clamped to the threshold — the number is a trigger, and clamping would destroy the
 * margin the result is reported with.
 */
@Component
@Order(30)
public class PointsThresholdCondition implements MatchEndCondition {

  private final GameProperties gameProperties;

  public PointsThresholdCondition(GameProperties gameProperties) {
    this.gameProperties = gameProperties;
  }

  @Override
  public Optional<MatchEndReason> evaluate(GameWithFighters match) {
    boolean crossed = match.fighters().values().stream().anyMatch(this::hasCrossedThreshold);
    return crossed ? Optional.of(MatchEndReason.POINTS_THRESHOLD) : Optional.empty();
  }

  private boolean hasCrossedThreshold(Player fighter) {
    return fighter.getPoints().getNumOfPoints() >= gameProperties.winningPoints();
  }
}

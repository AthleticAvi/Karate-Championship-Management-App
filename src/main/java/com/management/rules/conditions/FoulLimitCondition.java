package com.management.rules.conditions;

import com.management.config.GameProperties;
import com.management.enums.MatchEndReason;
import com.management.models.GameWithFighters;
import com.management.rules.MatchEndCondition;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** A fighter accumulated the configured number of fouls. */
@Component
@Order(20)
public class FoulLimitCondition implements MatchEndCondition {

  private final GameProperties gameProperties;

  public FoulLimitCondition(GameProperties gameProperties) {
    this.gameProperties = gameProperties;
  }

  @Override
  public Optional<MatchEndReason> evaluate(GameWithFighters match) {
    boolean reached =
        match.fighters().values().stream()
            .anyMatch(
                fighter -> fighter.getFouls().getNumOfFouls() >= gameProperties.foulsEndingMatch());
    return reached ? Optional.of(MatchEndReason.FOUL_LIMIT) : Optional.empty();
  }
}

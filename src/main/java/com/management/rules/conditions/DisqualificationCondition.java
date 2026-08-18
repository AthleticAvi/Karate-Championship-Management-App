package com.management.rules.conditions;

import com.management.enums.MatchEndReason;
import com.management.models.GameWithFighters;
import com.management.models.Player;
import com.management.rules.MatchEndCondition;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A fighter is out of the match.
 *
 * <p>Disqualification is issued by a referee rather than detected, but it still needs a condition:
 * the flag is set on the fighter, and this is what turns that into the match ending. Keeping the
 * two separate means the same code path ends the match however the flag came to be set.
 */
@Component
@Order(10)
public class DisqualificationCondition implements MatchEndCondition {

  @Override
  public Optional<MatchEndReason> evaluate(GameWithFighters match) {
    return match.fighters().values().stream().anyMatch(Player::isDisqualified)
        ? Optional.of(MatchEndReason.DISQUALIFICATION)
        : Optional.empty();
  }
}

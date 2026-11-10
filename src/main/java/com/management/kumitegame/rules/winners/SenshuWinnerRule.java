package com.management.kumitegame.rules.winners;

import com.management.kumitegame.domain.GameWithFighters;
import com.management.kumitegame.domain.MatchEndReason;
import com.management.kumitegame.domain.MatchOutcome;
import com.management.kumitegame.rules.WinnerRule;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Level scores are decided by SENSHU — whoever scored first.
 *
 * <p>Runs after the score comparison, because it only applies when the scores are level. The value
 * it reads is captured at the moment of the first score; nothing here can reconstruct it, which is
 * why the capture happens during scoring rather than at the end.
 */
@Component
@Order(30)
public class SenshuWinnerRule implements WinnerRule {

  @Override
  public Optional<MatchOutcome> decide(GameWithFighters match, MatchEndReason reason) {
    return match
        .game()
        .getSenshu()
        .map(first -> new MatchOutcome(first, reason, SenshuWinnerRule.class.getSimpleName()));
  }
}

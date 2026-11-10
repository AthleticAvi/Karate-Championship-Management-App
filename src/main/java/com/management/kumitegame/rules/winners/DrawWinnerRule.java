package com.management.kumitegame.rules.winners;

import com.management.kumitegame.domain.GameWithFighters;
import com.management.kumitegame.domain.MatchEndReason;
import com.management.kumitegame.domain.MatchOutcome;
import com.management.kumitegame.rules.WinnerRule;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Nobody won: level scores, and neither fighter ever scored, so there is no SENSHU to break the
 * tie.
 *
 * <p>Lowest priority, and it always answers — a match that ends must record a result, and "drawn"
 * is a real one rather than the absence of one. Having it as a rule rather than a fallback branch
 * keeps the search total: the evaluator never has to handle "no rule applied".
 */
@Component
@Order(Integer.MAX_VALUE)
public class DrawWinnerRule implements WinnerRule {

  @Override
  public Optional<MatchOutcome> decide(GameWithFighters match, MatchEndReason reason) {
    return Optional.of(MatchOutcome.draw(reason, DrawWinnerRule.class.getSimpleName()));
  }
}

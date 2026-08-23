package com.management.rules.winners;

import com.management.enums.MatchEndReason;
import com.management.models.GameWithFighters;
import com.management.models.MatchOutcome;
import com.management.rules.WinnerRule;
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

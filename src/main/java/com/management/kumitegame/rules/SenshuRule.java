package com.management.kumitegame.rules;

import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * At time up on equal points, the side holding SENSHU wins (WKF Kumite Competition Rules 2026.01,
 * Article 12.2.1, Appendix 6). The same in both formats.
 */
@Component
@Order(50)
public class SenshuRule implements SuggestionRule {

  @Override
  public Optional<SuggestedResult> suggest(Standing standing) {
    if (!standing.timeUp()) {
      return Optional.empty();
    }
    return Optional.ofNullable(standing.senshuHolder())
        .map(side -> SuggestedResult.win(side, ResultReason.SENSHU));
  }
}

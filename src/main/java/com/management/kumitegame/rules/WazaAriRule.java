package com.management.kumitegame.rules;

import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.SideState;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * At time up, IPPON level too: more WAZA_ARI win (WKF Kumite Competition Rules 2026.01, Article
 * 12.2.3 b, Appendix 6). The same in both formats.
 */
@Component
@Order(70)
public class WazaAriRule implements SuggestionRule {

  @Override
  public Optional<SuggestedResult> suggest(Standing standing) {
    if (!standing.timeUp()) {
      return Optional.empty();
    }
    return Ahead.on(standing, SideState::wazaAriCount)
        .map(side -> SuggestedResult.win(side, ResultReason.WAZA_ARI));
  }
}

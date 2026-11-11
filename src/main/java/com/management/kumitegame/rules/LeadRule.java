package com.management.kumitegame.rules;

import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.SideState;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A lead of at least the game's lead to win ends it, whatever the clock shows (WKF Kumite
 * Competition Rules 2026.01, Articles 7.7, 12.2.1). The lead is read from the settings snapshot.
 * The same in both formats.
 */
@Component
@Order(30)
public class LeadRule implements SuggestionRule {

  @Override
  public Optional<SuggestedResult> suggest(Standing standing) {
    if (Math.abs(Ahead.lead(standing, SideState::total)) < standing.settings().leadToWin()) {
      return Optional.empty();
    }
    return Ahead.on(standing, SideState::total)
        .map(side -> SuggestedResult.win(side, ResultReason.LEAD));
  }
}

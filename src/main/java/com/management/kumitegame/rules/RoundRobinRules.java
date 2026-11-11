package com.management.kumitegame.rules;

import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import org.springframework.stereotype.Component;

/**
 * Round robin: a game still level is a draw, HIKIWAKE, unless it is an extra game, which the
 * officials decide by HANTEI (WKF Kumite Competition Rules 2026.01, Articles 12.2.4, 12.2.5,
 * Appendix 6).
 */
@Component
public class RoundRobinRules implements FormatRules {

  @Override
  public GameFormat format() {
    return GameFormat.ROUND_ROBIN;
  }

  @Override
  public SuggestedResult whenLevel(Standing standing) {
    return standing.extraGame()
        ? SuggestedResult.hantei()
        : new SuggestedResult(Outcome.DRAW, null, ResultReason.HIKIWAKE, false);
  }
}

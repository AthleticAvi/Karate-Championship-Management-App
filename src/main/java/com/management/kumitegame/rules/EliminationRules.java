package com.management.kumitegame.rules;

import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import org.springframework.stereotype.Component;

/**
 * Elimination cannot end level, so the officials decide it by HANTEI (WKF Kumite Competition Rules
 * 2026.01, Articles 12.2.4, 12.2.5, Appendix 6).
 */
@Component
public class EliminationRules implements FormatRules {

  @Override
  public GameFormat format() {
    return GameFormat.ELIMINATION;
  }

  @Override
  public SuggestedResult whenLevel(Standing standing) {
    return SuggestedResult.hantei();
  }
}

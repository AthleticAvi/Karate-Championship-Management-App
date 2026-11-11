package com.management.kumitegame.rules;

import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.SideState;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A HANSOKU or SHIKKAKU against one side: the other wins, for that reason, SHIKKAKU when the side
 * has both (WKF Kumite Competition Rules 2026.01, Article 12.2.1). The same in both formats.
 */
@Component
@Order(20)
public class DisqualificationRule implements SuggestionRule {

  @Override
  public Optional<SuggestedResult> suggest(Standing standing) {
    return Stream.of(Side.values())
        .filter(side -> standing.side(side).isDisqualified())
        .filter(side -> !standing.side(side.opponent()).isDisqualified())
        .findFirst()
        .map(offender -> SuggestedResult.win(offender.opponent(), reason(standing.side(offender))));
  }

  private static ResultReason reason(SideState offender) {
    return offender.shikkakuCount() > 0 ? ResultReason.SHIKKAKU : ResultReason.HANSOKU;
  }
}

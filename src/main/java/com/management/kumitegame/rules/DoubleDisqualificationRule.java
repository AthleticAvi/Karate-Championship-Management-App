package com.management.kumitegame.rules;

import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.SideState;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Both sides disqualified by HANSOKU or SHIKKAKU: no result, so the next-round opponents win by
 * bye. A medal game is decided instead by the points at that moment, then SENSHU, then HANTEI,
 * keeping the fact that both were disqualified (WKF Kumite Competition Rules 2026.01, Articles
 * 12.2.11, 10.7.1). The same in both formats.
 *
 * <p>No medal goes to an athlete disqualified by SHIKKAKU, which is the championship's to apply:
 * the game suggests only who won it.
 */
@Component
@Order(10)
public class DoubleDisqualificationRule implements SuggestionRule {

  @Override
  public Optional<SuggestedResult> suggest(Standing standing) {
    if (!standing.side(Side.AKA).isDisqualified() || !standing.side(Side.AO).isDisqualified()) {
      return Optional.empty();
    }
    if (!standing.medalGame()) {
      return Optional.of(
          new SuggestedResult(Outcome.NO_RESULT, null, ResultReason.DOUBLE_DISQUALIFICATION, true));
    }
    return Optional.of(
        Ahead.on(standing, SideState::total)
            .map(side -> SuggestedResult.win(side, ResultReason.POINTS))
            .or(() ->
                Optional.ofNullable(standing.senshuHolder())
                    .map(side -> SuggestedResult.win(side, ResultReason.SENSHU)))
            .orElseGet(SuggestedResult::hantei)
            .withBothDisqualified());
  }
}

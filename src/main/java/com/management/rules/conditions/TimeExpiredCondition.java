package com.management.rules.conditions;

import com.management.enums.GameState;
import com.management.enums.MatchEndReason;
import com.management.models.GameWithFighters;
import com.management.rules.MatchEndCondition;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The clock reached zero.
 *
 * <p><strong>Observed, not pushed.</strong> Nothing wakes up when the clock hits zero — the timer
 * is derived from stored values rather than running as a scheduled task — so expiry is noticed the
 * next time something asks, which is after the next point or foul. A match left untouched past its
 * duration therefore stays open until someone interacts with it. Closing that gap needs the clock
 * to emit events, which is the timer work's job, not this condition's.
 *
 * <p>Only a running match expires: a paused clock is not counting, and a queued one has not
 * started.
 */
@Component
@Order(40)
public class TimeExpiredCondition implements MatchEndCondition {

  @Override
  public Optional<MatchEndReason> evaluate(GameWithFighters match) {
    if (match.game().getGameState() != GameState.RUNNING) {
      return Optional.empty();
    }
    return match.game().getTimer().getRemainingTime().equals(Duration.ZERO)
        ? Optional.of(MatchEndReason.TIME_EXPIRED)
        : Optional.empty();
  }
}

package com.management.kumitegame.rules.conditions;

import com.management.kumitegame.domain.GameState;
import com.management.kumitegame.domain.GameWithFighters;
import com.management.kumitegame.domain.MatchEndReason;
import com.management.kumitegame.rules.MatchEndCondition;
import java.time.Clock;
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

  private final Clock clock;

  public TimeExpiredCondition(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Optional<MatchEndReason> evaluate(GameWithFighters match) {
    if (match.game().getGameState() != GameState.RUNNING) {
      return Optional.empty();
    }
    return match.game().timer(clock).getRemainingTime().equals(Duration.ZERO)
        ? Optional.of(MatchEndReason.TIME_EXPIRED)
        : Optional.empty();
  }
}

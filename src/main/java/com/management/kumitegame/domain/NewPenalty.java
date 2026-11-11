package com.management.kumitegame.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A penalty as the client sent it. */
public record NewPenalty(
    String id,
    Side side,
    PenaltyLevel level,
    @Nullable Infraction infraction,
    @Nullable Integer exchange,
    @Nullable Long clockRemainingMs) {

  /**
   * The same request: only what the client sent, and who sent it, are compared. An exchange the
   * client left out was filled in by the server, so it is not compared.
   */
  boolean matches(Penalty stored, @Nullable Side storedSide, String recordedBy) {
    return side == storedSide
        && level == stored.level()
        && infraction == stored.infraction()
        && (exchange == null || exchange.equals(stored.exchange()))
        && Objects.equals(clockRemainingMs, stored.clockRemainingMs())
        && recordedBy.equals(stored.meta().recordedBy());
  }
}

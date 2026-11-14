package com.management.kumitegame.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A score as the client sent it.
 *
 * @param exchange an earlier exchange a video review names; empty means the current one
 */
public record NewScore(
    String id,
    Side side,
    ScoreLevel level,
    ScoreSource source,
    @Nullable Integer exchange,
    @Nullable Long clockRemainingMs) {

  /**
   * The same request: only what the client sent, and who sent it, are compared. The exchange is
   * compared as sent: one left out matches only an exchange the server filled in.
   */
  boolean matches(Score stored, @Nullable Side storedSide, String recordedBy) {
    return side == storedSide
        && level == stored.level()
        && source == stored.source()
        && Objects.equals(exchange, stored.exchangeNamed() ? stored.exchange() : null)
        && Objects.equals(clockRemainingMs, stored.clockRemainingMs())
        && recordedBy.equals(stored.meta().recordedBy());
  }
}

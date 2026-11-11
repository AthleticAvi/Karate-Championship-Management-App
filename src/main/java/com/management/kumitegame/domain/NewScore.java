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
   * The same request: only what the client sent, and who sent it, are compared. An exchange the
   * client left out was filled in by the server, so it is not compared.
   */
  boolean matches(Score stored, @Nullable Side storedSide, String recordedBy) {
    return side == storedSide
        && level == stored.level()
        && source == stored.source()
        && (exchange == null || exchange.equals(stored.exchange()))
        && Objects.equals(clockRemainingMs, stored.clockRemainingMs())
        && recordedBy.equals(stored.meta().recordedBy());
  }
}

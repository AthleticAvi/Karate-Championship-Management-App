package com.management.kumitegame.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A SENSHU record as the client sent it.
 *
 * @param exchange an earlier exchange a video review names; empty means the current one
 */
public record NewSenshuRecord(
    String id,
    Side side,
    SenshuRecordKind kind,
    @Nullable String reason,
    @Nullable Integer exchange,
    @Nullable Long clockRemainingMs) {

  /**
   * The same request: only what the client sent, and who sent it, are compared. The exchange is
   * compared as sent: one left out matches only an exchange the server filled in.
   */
  boolean matches(SenshuRecord stored, String recordedBy) {
    return side == stored.side()
        && kind == stored.kind()
        && Objects.equals(reason, stored.reason())
        && Objects.equals(exchange, stored.exchangeNamed() ? stored.exchange() : null)
        && Objects.equals(clockRemainingMs, stored.clockRemainingMs())
        && recordedBy.equals(stored.meta().recordedBy());
  }
}

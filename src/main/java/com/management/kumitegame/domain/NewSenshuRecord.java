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
   * The same request: only what the client sent, and who sent it, are compared. An exchange the
   * client left out was filled in by the server, so it is not compared.
   */
  boolean matches(SenshuRecord stored, String recordedBy) {
    return side == stored.side()
        && kind == stored.kind()
        && Objects.equals(reason, stored.reason())
        && (exchange == null || exchange.equals(stored.exchange()))
        && Objects.equals(clockRemainingMs, stored.clockRemainingMs())
        && recordedBy.equals(stored.meta().recordedBy());
  }
}

package com.management.kumitegame.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** An amendment as the client sent it. */
public record NewAmendment(
    String id, AmendmentKind kind, String targetId, @Nullable String reason) {

  /** The same request: only what the client sent, and who sent it, are compared. */
  boolean matches(Amendment stored, String recordedBy) {
    return kind == stored.kind()
        && targetId.equals(stored.targetId())
        && Objects.equals(reason, stored.reason())
        && recordedBy.equals(stored.meta().recordedBy());
  }
}

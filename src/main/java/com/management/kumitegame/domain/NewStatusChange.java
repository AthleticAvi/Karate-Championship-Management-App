package com.management.kumitegame.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A status move as the client sent it. */
public record NewStatusChange(String id, StatusChangeKind kind, @Nullable String reason) {

  /** The same request: only what the client sent, and who sent it, are compared. */
  boolean matches(StatusChange stored, String recordedBy) {
    return kind == stored.kind()
        && Objects.equals(reason, stored.reason())
        && recordedBy.equals(stored.meta().recordedBy());
  }
}

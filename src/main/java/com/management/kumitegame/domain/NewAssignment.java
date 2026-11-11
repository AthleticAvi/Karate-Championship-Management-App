package com.management.kumitegame.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** An assignment as the client sent it. */
public record NewAssignment(
    String id,
    AssignmentKind kind,
    @Nullable Side side,
    @Nullable String athleteId,
    @Nullable String refereeId,
    @Nullable PanelRole role) {

  /** The same request: only what the client sent, and who sent it, are compared. */
  boolean matches(Assignment stored, String recordedBy) {
    return kind == stored.kind()
        && side == stored.side()
        && Objects.equals(athleteId, stored.athleteId())
        && Objects.equals(refereeId, stored.refereeId())
        && role == stored.role()
        && recordedBy.equals(stored.meta().recordedBy());
  }
}

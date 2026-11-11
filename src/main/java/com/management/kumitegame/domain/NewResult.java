package com.management.kumitegame.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A result as the client sent it. */
public record NewResult(
    String id,
    Outcome outcome,
    @Nullable Side winner,
    ResultReason reason,
    @Nullable String reasonDetail,
    boolean bothDisqualified) {

  /** The same request: only what the client sent, and who sent it, are compared. */
  boolean matches(Result stored, String recordedBy) {
    return outcome == stored.outcome()
        && winner == stored.winner()
        && reason == stored.reason()
        && Objects.equals(reasonDetail, stored.reasonDetail())
        && bothDisqualified == stored.bothDisqualified()
        && recordedBy.equals(stored.meta().recordedBy());
  }
}

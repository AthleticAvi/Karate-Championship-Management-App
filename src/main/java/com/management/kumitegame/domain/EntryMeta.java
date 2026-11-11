package com.management.kumitegame.domain;

import java.time.Instant;
import java.util.List;

/**
 * What every entry carries, composed into each one rather than inherited.
 *
 * @param id given by the client, one per action, and the key a retry is recognised by
 * @param order one counter across the whole game, so the lists read in order tell the game
 * @param recordedAt the server's instant
 * @param recordedBy the signed-in account that recorded it
 * @param raisedWarnings what the action breached, applied all the same
 */
public record EntryMeta(
    String id, int order, Instant recordedAt, String recordedBy, List<Warning> raisedWarnings) {

  public EntryMeta {
    raisedWarnings = List.copyOf(raisedWarnings);
  }
}

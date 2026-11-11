package com.management.kumitegame.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Everything derived from a game's entries, folded in order. Never stored.
 *
 * @param exchange the number of main-clock starts so far
 * @param currentResult the latest result; none after a reopen of a DECIDED game supersedes it, or
 *     after a void
 * @param timeUpAt when the main clock reached zero, while the game is TIME_UP
 * @param senshu who holds SENSHU, and whether it is closed
 * @param cancelledIds the scores, penalties and SENSHU records a CANCEL took back
 * @param warnings what the fold found: the format, or history it skipped
 * @param lastOrder the highest order used, so the next entry takes the one after
 */
public record GameState(
    GameStatus status,
    ClockState mainClock,
    Map<Side, ClockState> medicalClocks,
    int exchange,
    Map<Side, SideState> sides,
    List<PanelMember> panel,
    @Nullable Result currentResult,
    @Nullable Instant timeUpAt,
    SenshuState senshu,
    Set<String> cancelledIds,
    List<GameWarning> warnings,
    int lastOrder) {

  public GameState {
    medicalClocks = Map.copyOf(medicalClocks);
    sides = Map.copyOf(sides);
    panel = List.copyOf(panel);
    cancelledIds = Set.copyOf(cancelledIds);
    warnings = List.copyOf(warnings);
  }

  public SideState side(Side side) {
    return Objects.requireNonNull(sides.get(side));
  }

  public ClockState medicalClock(Side side) {
    return Objects.requireNonNull(medicalClocks.get(side));
  }

  /** The main clock, or the side's medical timer. */
  public ClockState clock(Timer timer, @Nullable Side side) {
    return timer == Timer.MEDICAL && side != null ? medicalClock(side) : mainClock;
  }

  public boolean isCancelled(String entryId) {
    return cancelledIds.contains(entryId);
  }

  /** The exchange an entry made now belongs to: empty before the first start. */
  @Nullable Integer currentExchange() {
    return exchange == 0 ? null : exchange;
  }
}

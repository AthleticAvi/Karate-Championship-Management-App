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
 * @param suggested the result the rules point to now; empty while they point to none, and on a
 *     VOIDED game
 * @param effectiveScores each side's score as the format counts it: in round robin, after a
 *     forfeit, not what it scored (Article 10.7.1)
 * @param cancelledIds the scores, penalties and SENSHU records a CANCEL took back
 * @param warnings what the fold found: the format, history it skipped, or result rules that failed
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
    @Nullable SuggestedResult suggested,
    Map<Side, EffectiveScore> effectiveScores,
    Set<String> cancelledIds,
    List<GameWarning> warnings,
    int lastOrder) {

  public GameState {
    medicalClocks = Map.copyOf(medicalClocks);
    sides = Map.copyOf(sides);
    effectiveScores = Map.copyOf(effectiveScores);
    panel = List.copyOf(panel);
    cancelledIds = Set.copyOf(cancelledIds);
    warnings = List.copyOf(warnings);
  }

  public SideState side(Side side) {
    return Objects.requireNonNull(sides.get(side));
  }

  public EffectiveScore effectiveScore(Side side) {
    return Objects.requireNonNull(effectiveScores.get(side));
  }

  /**
   * Whether the side is out of the game: a HANSOKU or SHIKKAKU stands against it, or the result
   * that stands disqualifies it. That is a WIN by HANSOKU or SHIKKAKU it lost, or a result that
   * disqualifies both sides. A KIKEN is a withdrawal, not a disqualification. The result rules read
   * the penalties alone: see {@link SideState#isDisqualified()}.
   */
  public boolean isDisqualified(Side side) {
    return side(side).isDisqualified()
        || (currentResult != null && disqualifies(currentResult, side));
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

  /** Only a WIN names a winner, so a side with a winning opponent lost a WIN. */
  private static boolean disqualifies(Result result, Side side) {
    if (result.bothDisqualified() || result.reason() == ResultReason.DOUBLE_DISQUALIFICATION) {
      return true;
    }
    return result.winner() == side.opponent()
        && (result.reason() == ResultReason.HANSOKU || result.reason() == ResultReason.SHIKKAKU);
  }
}

package com.management.kumitegame.web;

import com.management.kumitegame.domain.AmendmentKind;
import com.management.kumitegame.domain.AssignmentKind;
import com.management.kumitegame.domain.ClockActionKind;
import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.GameStatus;
import com.management.kumitegame.domain.Infraction;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.PanelRole;
import com.management.kumitegame.domain.PauseReason;
import com.management.kumitegame.domain.PenaltyLevel;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.ScoreSource;
import com.management.kumitegame.domain.SenshuRecordKind;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.StatusChangeKind;
import com.management.kumitegame.domain.Timer;
import com.management.kumitegame.domain.Warning;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A game as the scorekeeper sees it: every entry, and everything derived from them.
 *
 * <p>Durations are whole milliseconds, instants ISO-8601 in UTC. A running clock's {@code
 * remainingMs} is the time left at {@code serverTime}: a screen counts down from there, and resyncs
 * on every answer.
 *
 * @param exchange the number of main-clock starts so far
 * @param senshuClosed SENSHU was withdrawn in the final window, so nobody should earn it again
 * @param currentResult the latest result; empty while none is recorded, after a reopen of a DECIDED
 *     game supersedes it, and on a VOIDED game
 * @param statusChanges every approve, reopen and void, as given
 * @param warnings what the game itself shows: its format, or stored history skipped while folding
 * @param serverTime the server's time when this was read
 */
public record KumiteGameResponse(
    String id,
    GameFormat format,
    boolean medalGame,
    boolean extraGame,
    SettingsResponse settings,
    GameStatus status,
    int exchange,
    ClockResponse mainClock,
    SideResponse aka,
    SideResponse ao,
    List<PanelMemberResponse> panel,
    List<AssignmentResponse> assignments,
    List<ClockActionResponse> clockActions,
    boolean senshuClosed,
    List<SenshuRecordResponse> senshuRecords,
    List<AmendmentResponse> amendments,
    List<ResultResponse> results,
    @Nullable ResultResponse currentResult,
    List<StatusChangeResponse> statusChanges,
    List<GameWarningResponse> warnings,
    Instant serverTime) {

  /** The rule values the game runs under. */
  public record SettingsResponse(
      long durationMs,
      List<Long> durationsOfferedMs,
      Map<ScoreLevel, Integer> pointValues,
      int leadToWin,
      long finalWindowMs,
      long lateScoreWindowMs,
      int chuiLimit,
      int roundRobinForfeitPoints,
      List<Long> addTimeStepsMs,
      long medicalTimeMs,
      long timeShownToleranceMs,
      String rulebookVersion) {}

  /** A clock: whether it runs, and the time left at {@code serverTime}. */
  public record ClockResponse(boolean running, long remainingMs) {}

  /**
   * One side.
   *
   * @param colour how the side is shown, from configuration
   * @param athlete the latest athlete put on this side; empty only in history that names none
   * @param senshu whether this side holds SENSHU; never both sides
   */
  public record SideResponse(
      String colour,
      @Nullable ParticipantResponse athlete,
      boolean senshu,
      ClockResponse medicalClock,
      int total,
      int ipponCount,
      int wazaAriCount,
      int chuiCount,
      int hansokuChuiCount,
      List<ScoreResponse> scores,
      List<PenaltyResponse> penalties) {}

  /**
   * An athlete or referee the game names, looked up when it is read.
   *
   * @param name empty when it no longer exists
   * @param known false when it no longer exists
   */
  public record ParticipantResponse(String id, @Nullable String name, boolean known) {}

  /** A referee on the panel now. */
  public record PanelMemberResponse(ParticipantResponse referee, PanelRole role) {}

  /** What every entry carries. */
  public record EntryMetaResponse(
      String id, int order, Instant recordedAt, String recordedBy, List<Warning> raisedWarnings) {}

  /**
   * A score as given.
   *
   * @param cancelled whether a CANCEL took it back
   */
  public record ScoreResponse(
      ScoreLevel level,
      ScoreSource source,
      @Nullable Integer exchange,
      @Nullable Long clockRemainingMs,
      boolean cancelled,
      EntryMetaResponse meta) {}

  /**
   * A penalty as given.
   *
   * @param cancelled whether a CANCEL took it back
   */
  public record PenaltyResponse(
      PenaltyLevel level,
      @Nullable Infraction infraction,
      @Nullable Integer exchange,
      @Nullable Long clockRemainingMs,
      boolean cancelled,
      EntryMetaResponse meta) {}

  public record AssignmentResponse(
      AssignmentKind kind,
      @Nullable Side side,
      @Nullable String athleteId,
      @Nullable String refereeId,
      @Nullable PanelRole role,
      EntryMetaResponse meta) {}

  public record ClockActionResponse(
      Timer timer,
      @Nullable Side side,
      ClockActionKind action,
      long timeShownMs,
      @Nullable Long amountMs,
      @Nullable PauseReason pauseReason,
      EntryMetaResponse meta) {}

  /**
   * SENSHU awarded or withdrawn, as given.
   *
   * @param cancelled whether a CANCEL took it back
   */
  public record SenshuRecordResponse(
      Side side,
      SenshuRecordKind kind,
      @Nullable String reason,
      @Nullable Integer exchange,
      @Nullable Long clockRemainingMs,
      boolean cancelled,
      EntryMetaResponse meta) {}

  public record AmendmentResponse(
      AmendmentKind kind, String targetId, @Nullable String reason, EntryMetaResponse meta) {}

  public record ResultResponse(
      Outcome outcome,
      @Nullable Side winner,
      ResultReason reason,
      @Nullable String reasonDetail,
      boolean bothDisqualified,
      EntryMetaResponse meta) {}

  public record StatusChangeResponse(
      StatusChangeKind kind, @Nullable String reason, EntryMetaResponse meta) {}

  /**
   * A warning the game itself shows.
   *
   * @param entryId the entry it concerns; empty when it concerns the game
   */
  public record GameWarningResponse(Warning code, @Nullable String entryId) {}
}

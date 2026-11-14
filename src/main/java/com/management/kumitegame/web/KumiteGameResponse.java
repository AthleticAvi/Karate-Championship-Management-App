package com.management.kumitegame.web;

import com.fasterxml.jackson.annotation.JsonView;
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
 * A game: every entry, and everything derived from them. The scorekeeper's view holds every field;
 * the public view, the same record, leaves out the fields marked {@link GameViews.Scorekeeper}: who
 * recorded what, the warnings, and every note in free text.
 *
 * <p>Durations are whole milliseconds, instants ISO-8601 in UTC. A running clock's {@code
 * remainingMs} is the time left at {@code serverTime}: a screen counts down from there, anchored
 * once by the answer's age, and starts again only from an answer with a new tag. A 304, or a
 * browser's cached copy of this answer, keeps the countdown going: see {@code [countdown]} in
 * {@code src/main/java/com/management/web/README.md}.
 *
 * @param version the stored game's version this answer shows: sent back as {@code expectedVersion}
 *     on setting the time, a result, a settings change or a status change
 * @param exchange the number of main-clock starts so far
 * @param senshuClosed SENSHU was withdrawn in the final window, so nobody should earn it again
 * @param currentResult the latest result; empty while none is recorded, after a reopen of a DECIDED
 *     game supersedes it, and on a VOIDED game
 * @param suggestedResult the result the rules point to now; the scorekeeper confirms it by
 *     recording a result. Empty while they point to none
 * @param statusChanges every approve, reopen and void, as given
 * @param warnings what the game itself shows: its format, stored history skipped while folding, or
 *     no suggestion because the result rules failed; left out of the public view
 * @param serverTime the server's time when this was read
 */
public record KumiteGameResponse(
    String id,
    long version,
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
    @Nullable SuggestedResultResponse suggestedResult,
    List<StatusChangeResponse> statusChanges,
    @JsonView(GameViews.Scorekeeper.class) List<GameWarningResponse> warnings,
    Instant serverTime) {

  /**
   * The rule values the game runs under, in both views.
   *
   * @param panelLimit the most referees the panel holds; empty for a game created before the limit
   *     existed, which has none
   */
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
      @Nullable Integer panelLimit,
      String rulebookVersion) {}

  /** A clock: whether it runs, and the time left at {@code serverTime}. */
  public record ClockResponse(boolean running, long remainingMs) {}

  /**
   * One side.
   *
   * @param colour how the side is shown, from configuration
   * @param athlete the latest athlete put on this side; empty only in history that names none
   * @param senshu whether this side holds SENSHU; never both sides
   * @param hansokuCount the HANSOKU standing against this side: not cancelled
   * @param shikkakuCount the SHIKKAKU standing against this side: not cancelled
   * @param disqualified a HANSOKU or SHIKKAKU stands against this side, or the result that stands
   *     disqualifies it: a WIN by HANSOKU or SHIKKAKU it lost, or both sides disqualified
   * @param effectiveScore the score the format counts: in round robin after HANSOKU, KIKEN or
   *     SHIKKAKU, not what was scored. Otherwise what was scored
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
      int hansokuCount,
      int shikkakuCount,
      boolean disqualified,
      EffectiveScoreResponse effectiveScore,
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

  /**
   * What every entry carries.
   *
   * @param recordedBy the account that recorded it; left out of the public view
   * @param raisedWarnings what recording it raised; left out of the public view
   */
  public record EntryMetaResponse(
      String id,
      int order,
      Instant recordedAt,
      @JsonView(GameViews.Scorekeeper.class) String recordedBy,
      @JsonView(GameViews.Scorekeeper.class) List<Warning> raisedWarnings) {}

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
   * @param reason a note in free text; left out of the public view
   * @param cancelled whether a CANCEL took it back
   */
  public record SenshuRecordResponse(
      Side side,
      SenshuRecordKind kind,
      @JsonView(GameViews.Scorekeeper.class) @Nullable String reason,
      @Nullable Integer exchange,
      @Nullable Long clockRemainingMs,
      boolean cancelled,
      EntryMetaResponse meta) {}

  /**
   * A correction to an entry.
   *
   * @param reason a note in free text; left out of the public view
   */
  public record AmendmentResponse(
      AmendmentKind kind,
      String targetId,
      @JsonView(GameViews.Scorekeeper.class) @Nullable String reason,
      EntryMetaResponse meta) {}

  /**
   * A result as given.
   *
   * @param reasonDetail a note in free text; left out of the public view, where {@code reason}
   *     stays
   * @param suggested what the rules suggested when it was recorded; empty when they suggested
   *     nothing
   */
  public record ResultResponse(
      Outcome outcome,
      @Nullable Side winner,
      ResultReason reason,
      @JsonView(GameViews.Scorekeeper.class) @Nullable String reasonDetail,
      boolean bothDisqualified,
      @Nullable SuggestedResultResponse suggested,
      EntryMetaResponse meta) {}

  /**
   * A result the rules point to.
   *
   * @param winner for a WIN, but never for a HANTEI: the officials' vote names it
   */
  public record SuggestedResultResponse(
      Outcome outcome, @Nullable Side winner, ResultReason reason, boolean bothDisqualified) {}

  /**
   * A side's score as the format counts it.
   *
   * @param addedPoints points given rather than scored, counted as YUKO
   */
  public record EffectiveScoreResponse(
      int total, int ipponCount, int wazaAriCount, int addedPoints) {}

  /**
   * An approve, reopen or void.
   *
   * @param reason a note in free text; left out of the public view
   */
  public record StatusChangeResponse(
      StatusChangeKind kind,
      @JsonView(GameViews.Scorekeeper.class) @Nullable String reason,
      EntryMetaResponse meta) {}

  /**
   * A warning the game itself shows.
   *
   * @param entryId the entry it concerns; empty when it concerns the game
   */
  public record GameWarningResponse(Warning code, @Nullable String entryId) {}
}

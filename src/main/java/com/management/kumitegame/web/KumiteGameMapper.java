package com.management.kumitegame.web;

import com.management.athlete.domain.Athlete;
import com.management.kumitegame.domain.Amendment;
import com.management.kumitegame.domain.Assignment;
import com.management.kumitegame.domain.ClockAction;
import com.management.kumitegame.domain.ClockState;
import com.management.kumitegame.domain.EffectiveScore;
import com.management.kumitegame.domain.EntryMeta;
import com.management.kumitegame.domain.GameRead;
import com.management.kumitegame.domain.GameState;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.NewAmendment;
import com.management.kumitegame.domain.NewAssignment;
import com.management.kumitegame.domain.NewClockAction;
import com.management.kumitegame.domain.NewGame;
import com.management.kumitegame.domain.NewPenalty;
import com.management.kumitegame.domain.NewResult;
import com.management.kumitegame.domain.NewScore;
import com.management.kumitegame.domain.NewSenshuRecord;
import com.management.kumitegame.domain.NewSettings;
import com.management.kumitegame.domain.NewStatusChange;
import com.management.kumitegame.domain.PanelMember;
import com.management.kumitegame.domain.Penalty;
import com.management.kumitegame.domain.Recording;
import com.management.kumitegame.domain.Result;
import com.management.kumitegame.domain.Score;
import com.management.kumitegame.domain.SenshuRecord;
import com.management.kumitegame.domain.Settings;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.SideState;
import com.management.kumitegame.domain.StatusChange;
import com.management.kumitegame.domain.SuggestedResult;
import com.management.kumitegame.web.KumiteGameResponse.AmendmentResponse;
import com.management.kumitegame.web.KumiteGameResponse.AssignmentResponse;
import com.management.kumitegame.web.KumiteGameResponse.ClockActionResponse;
import com.management.kumitegame.web.KumiteGameResponse.ClockResponse;
import com.management.kumitegame.web.KumiteGameResponse.EffectiveScoreResponse;
import com.management.kumitegame.web.KumiteGameResponse.EntryMetaResponse;
import com.management.kumitegame.web.KumiteGameResponse.GameWarningResponse;
import com.management.kumitegame.web.KumiteGameResponse.PanelMemberResponse;
import com.management.kumitegame.web.KumiteGameResponse.ParticipantResponse;
import com.management.kumitegame.web.KumiteGameResponse.PenaltyResponse;
import com.management.kumitegame.web.KumiteGameResponse.ResultResponse;
import com.management.kumitegame.web.KumiteGameResponse.ScoreResponse;
import com.management.kumitegame.web.KumiteGameResponse.SenshuRecordResponse;
import com.management.kumitegame.web.KumiteGameResponse.SettingsResponse;
import com.management.kumitegame.web.KumiteGameResponse.SideResponse;
import com.management.kumitegame.web.KumiteGameResponse.StatusChangeResponse;
import com.management.kumitegame.web.KumiteGameResponse.SuggestedResultResponse;
import com.management.kumitegame.web.KumiteGameSummaryResponse.ResultSummaryResponse;
import com.management.kumitegame.web.KumiteGameSummaryResponse.SideSummaryResponse;
import com.management.referee.domain.Referee;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Turns a game as read into the game the API reports, and each request into domain input.
 *
 * <p>The colour each side is shown in is configuration, passed in: rules and storage know only the
 * side. An athlete or referee the game names that no longer exists reads as unknown.
 */
public final class KumiteGameMapper {

  private KumiteGameMapper() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  public static RecordedResponse toResponse(Recording recording, Map<Side, String> colours) {
    return new RecordedResponse(toResponse(recording.game(), colours), recording.raisedWarnings());
  }

  public static KumiteGameResponse toResponse(GameRead read, Map<Side, String> colours) {
    KumiteGame game = read.game();
    GameState state = read.state();
    Instant now = read.serverTime();
    Result current = state.currentResult();
    return new KumiteGameResponse(
        game.id(),
        game.format(),
        game.isMedalGame(),
        game.isExtraGame(),
        settings(game.settings()),
        state.status(),
        state.exchange(),
        clock(state.mainClock(), now),
        side(Side.AKA, read, colours),
        side(Side.AO, read, colours),
        state.panel().stream()
            .map(member ->
                new PanelMemberResponse(
                    referee(member.refereeId(), read.referees()), member.role()))
            .toList(),
        game.assignments().stream().map(KumiteGameMapper::assignment).toList(),
        game.clockActions().stream().map(KumiteGameMapper::clockAction).toList(),
        state.senshu().closed(),
        game.senshuRecords().stream().map(record -> senshuRecord(record, state)).toList(),
        game.amendments().stream().map(KumiteGameMapper::amendment).toList(),
        game.results().stream().map(KumiteGameMapper::result).toList(),
        current == null ? null : result(current),
        suggested(state.suggested()),
        game.statusChanges().stream().map(KumiteGameMapper::statusChange).toList(),
        state.warnings().stream()
            .map(warning -> new GameWarningResponse(warning.code(), warning.entryId()))
            .toList(),
        now);
  }

  /** A game as a list shows it: where it stands, who fights, and the result that stands. */
  public static KumiteGameSummaryResponse toSummary(GameRead read, Map<Side, String> colours) {
    Result current = read.state().currentResult();
    return new KumiteGameSummaryResponse(
        read.game().id(),
        read.state().status(),
        sideSummary(Side.AKA, read, colours),
        sideSummary(Side.AO, read, colours),
        current == null
            ? null
            : new ResultSummaryResponse(
                current.outcome(), current.winner(), current.reason(), current.bothDisqualified()));
  }

  public static NewGame toNewGame(KumiteGameRequest request) {
    List<PanelMemberRequest> panel = request.panel();
    return new NewGame(
        request.format(),
        request.medalGame(),
        request.extraGame(),
        request.akaAthleteId(),
        request.aoAthleteId(),
        panel == null
            ? List.of()
            : panel.stream()
                .map(member -> new PanelMember(member.refereeId(), member.role()))
                .toList());
  }

  public static NewSettings toNewSettings(SettingsRequest request) {
    return new NewSettings(
        request.durationMs(),
        request.durationsOfferedMs(),
        request.pointValues(),
        request.leadToWin(),
        request.finalWindowMs(),
        request.lateScoreWindowMs(),
        request.chuiLimit(),
        request.roundRobinForfeitPoints(),
        request.addTimeStepsMs(),
        request.medicalTimeMs(),
        request.timeShownToleranceMs());
  }

  public static NewScore toNewScore(ScoreRequest request) {
    return new NewScore(
        request.id(),
        request.side(),
        request.level(),
        request.source(),
        request.exchange(),
        request.clockRemainingMs());
  }

  public static NewPenalty toNewPenalty(PenaltyRequest request) {
    return new NewPenalty(
        request.id(),
        request.side(),
        request.level(),
        request.infraction(),
        request.exchange(),
        request.clockRemainingMs());
  }

  public static NewClockAction toNewClockAction(ClockActionRequest request) {
    return new NewClockAction(
        request.id(),
        request.timer(),
        request.side(),
        request.action(),
        request.timeShownMs(),
        request.amountMs(),
        request.pauseReason());
  }

  public static NewSenshuRecord toNewSenshuRecord(SenshuRecordRequest request) {
    return new NewSenshuRecord(
        request.id(),
        request.side(),
        request.kind(),
        request.reason(),
        request.exchange(),
        request.clockRemainingMs());
  }

  public static NewAssignment toNewAssignment(AssignmentRequest request) {
    return new NewAssignment(
        request.id(),
        request.kind(),
        request.side(),
        request.athleteId(),
        request.refereeId(),
        request.role());
  }

  public static NewAmendment toNewAmendment(AmendmentRequest request) {
    return new NewAmendment(request.id(), request.kind(), request.targetId(), request.reason());
  }

  public static NewResult toNewResult(ResultRequest request) {
    return new NewResult(
        request.id(),
        request.outcome(),
        request.winner(),
        request.reason(),
        request.reasonDetail(),
        request.bothDisqualified());
  }

  public static NewStatusChange toNewStatusChange(StatusChangeRequest request) {
    return new NewStatusChange(request.id(), request.kind(), request.reason());
  }

  private static SettingsResponse settings(Settings settings) {
    return new SettingsResponse(
        settings.durationMs(),
        settings.durationsOfferedMs(),
        settings.pointValues(),
        settings.leadToWin(),
        settings.finalWindowMs(),
        settings.lateScoreWindowMs(),
        settings.chuiLimit(),
        settings.roundRobinForfeitPoints(),
        settings.addTimeStepsMs(),
        settings.medicalTimeMs(),
        settings.timeShownToleranceMs(),
        settings.rulebookVersion());
  }

  private static ClockResponse clock(ClockState clock, Instant now) {
    return new ClockResponse(clock.running(), clock.remainingAt(now));
  }

  private static SideResponse side(Side side, GameRead read, Map<Side, String> colours) {
    GameState state = read.state();
    SideState tally = state.side(side);
    String athleteId = tally.athleteId();
    return new SideResponse(
        colour(side, colours),
        athleteId == null ? null : athlete(athleteId, read.athletes()),
        state.senshu().isHeldBy(side),
        clock(state.medicalClock(side), read.serverTime()),
        tally.total(),
        tally.ipponCount(),
        tally.wazaAriCount(),
        tally.chuiCount(),
        tally.hansokuChuiCount(),
        tally.hansokuCount(),
        tally.shikkakuCount(),
        tally.isDisqualified(),
        effectiveScore(state.effectiveScore(side)),
        read.game().scores(side).stream().map(score -> score(score, state)).toList(),
        read.game().penalties(side).stream().map(penalty -> penalty(penalty, state)).toList());
  }

  private static SideSummaryResponse sideSummary(
      Side side, GameRead read, Map<Side, String> colours) {
    String athleteId = read.state().side(side).athleteId();
    return new SideSummaryResponse(
        colour(side, colours), athleteId == null ? null : athlete(athleteId, read.athletes()));
  }

  private static String colour(Side side, Map<Side, String> colours) {
    return Objects.requireNonNull(colours.get(side), "every side has a colour");
  }

  private static ParticipantResponse athlete(String id, Map<String, Athlete> athletes) {
    Athlete athlete = athletes.get(id);
    return new ParticipantResponse(id, athlete == null ? null : athlete.name(), athlete != null);
  }

  private static ParticipantResponse referee(String id, Map<String, Referee> referees) {
    Referee referee = referees.get(id);
    return new ParticipantResponse(id, referee == null ? null : referee.name(), referee != null);
  }

  private static EntryMetaResponse meta(EntryMeta meta) {
    return new EntryMetaResponse(
        meta.id(), meta.order(), meta.recordedAt(), meta.recordedBy(), meta.raisedWarnings());
  }

  private static ScoreResponse score(Score score, GameState state) {
    return new ScoreResponse(
        score.level(),
        score.source(),
        score.exchange(),
        score.clockRemainingMs(),
        state.isCancelled(score.id()),
        meta(score.meta()));
  }

  private static PenaltyResponse penalty(Penalty penalty, GameState state) {
    return new PenaltyResponse(
        penalty.level(),
        penalty.infraction(),
        penalty.exchange(),
        penalty.clockRemainingMs(),
        state.isCancelled(penalty.id()),
        meta(penalty.meta()));
  }

  private static AssignmentResponse assignment(Assignment assignment) {
    return new AssignmentResponse(
        assignment.kind(),
        assignment.side(),
        assignment.athleteId(),
        assignment.refereeId(),
        assignment.role(),
        meta(assignment.meta()));
  }

  private static ClockActionResponse clockAction(ClockAction action) {
    return new ClockActionResponse(
        action.timer(),
        action.side(),
        action.action(),
        action.timeShownMs(),
        action.amountMs(),
        action.pauseReason(),
        meta(action.meta()));
  }

  private static SenshuRecordResponse senshuRecord(SenshuRecord record, GameState state) {
    return new SenshuRecordResponse(
        record.side(),
        record.kind(),
        record.reason(),
        record.exchange(),
        record.clockRemainingMs(),
        state.isCancelled(record.id()),
        meta(record.meta()));
  }

  private static AmendmentResponse amendment(Amendment amendment) {
    return new AmendmentResponse(
        amendment.kind(), amendment.targetId(), amendment.reason(), meta(amendment.meta()));
  }

  private static ResultResponse result(Result result) {
    return new ResultResponse(
        result.outcome(),
        result.winner(),
        result.reason(),
        result.reasonDetail(),
        result.bothDisqualified(),
        suggested(result.suggested()),
        meta(result.meta()));
  }

  private static @Nullable SuggestedResultResponse suggested(@Nullable SuggestedResult suggested) {
    return suggested == null
        ? null
        : new SuggestedResultResponse(
            suggested.outcome(),
            suggested.winner(),
            suggested.reason(),
            suggested.bothDisqualified());
  }

  private static EffectiveScoreResponse effectiveScore(EffectiveScore score) {
    return new EffectiveScoreResponse(
        score.total(), score.ipponCount(), score.wazaAriCount(), score.addedPoints());
  }

  private static StatusChangeResponse statusChange(StatusChange change) {
    return new StatusChangeResponse(change.kind(), change.reason(), meta(change.meta()));
  }
}

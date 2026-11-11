package com.management.kumitegame.data;

import com.management.kumitegame.data.KumiteGameDocument.StoredAmendment;
import com.management.kumitegame.data.KumiteGameDocument.StoredAssignment;
import com.management.kumitegame.data.KumiteGameDocument.StoredClockAction;
import com.management.kumitegame.data.KumiteGameDocument.StoredMeta;
import com.management.kumitegame.data.KumiteGameDocument.StoredPenalty;
import com.management.kumitegame.data.KumiteGameDocument.StoredResult;
import com.management.kumitegame.data.KumiteGameDocument.StoredScore;
import com.management.kumitegame.data.KumiteGameDocument.StoredSenshuRecord;
import com.management.kumitegame.data.KumiteGameDocument.StoredSettings;
import com.management.kumitegame.data.KumiteGameDocument.StoredSide;
import com.management.kumitegame.domain.Amendment;
import com.management.kumitegame.domain.AmendmentKind;
import com.management.kumitegame.domain.Assignment;
import com.management.kumitegame.domain.AssignmentKind;
import com.management.kumitegame.domain.ClockAction;
import com.management.kumitegame.domain.ClockActionKind;
import com.management.kumitegame.domain.EntryMeta;
import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.Infraction;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.PanelRole;
import com.management.kumitegame.domain.PauseReason;
import com.management.kumitegame.domain.Penalty;
import com.management.kumitegame.domain.PenaltyLevel;
import com.management.kumitegame.domain.Result;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.Score;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.ScoreSource;
import com.management.kumitegame.domain.SenshuRecord;
import com.management.kumitegame.domain.SenshuRecordKind;
import com.management.kumitegame.domain.Settings;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.Timer;
import com.management.kumitegame.domain.Warning;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns a stored game into the domain type, and the domain type back into what is stored.
 *
 * <p>The one place an old stored shape is absorbed: when {@link
 * KumiteGameDocument#CURRENT_SCHEMA_VERSION} rises, {@link #toDomain} reads every older shape, and
 * every write stores the current one. A shape newer than this build knows is refused, never guessed
 * at, such as after a rollback. A list stored as absent reads as empty.
 */
public final class KumiteGameDocumentMapper {

  private KumiteGameDocumentMapper() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  /**
   * A stored value this build cannot read, such as an enum name it no longer has, is a fault in the
   * stored data, never the caller's: it surfaces as an {@link IllegalStateException}, a 500, and
   * not as the 400 an {@link IllegalArgumentException} would answer.
   */
  public static KumiteGame toDomain(KumiteGameDocument document) {
    try {
      return read(document);
    } catch (IllegalArgumentException unreadable) {
      throw new IllegalStateException(
          "Game " + document.getId() + " is stored in a form this build cannot read", unreadable);
    }
  }

  private static KumiteGame read(KumiteGameDocument document) {
    if (document.getSchemaVersion() > KumiteGameDocument.CURRENT_SCHEMA_VERSION) {
      throw new IllegalStateException(
          "Game "
              + document.getId()
              + " is stored at schema version "
              + document.getSchemaVersion()
              + ", newer than this build reads");
    }
    Map<Side, List<Score>> scores = new EnumMap<>(Side.class);
    Map<Side, List<Penalty>> penalties = new EnumMap<>(Side.class);
    if (document.getSides() != null) {
      document
          .getSides()
          .forEach((name, side) -> {
            scores.put(Side.valueOf(name), list(side.scores(), KumiteGameDocumentMapper::score));
            penalties.put(
                Side.valueOf(name), list(side.penalties(), KumiteGameDocumentMapper::penalty));
          });
    }
    return new KumiteGame(
        document.getId(),
        GameFormat.valueOf(document.getFormat()),
        document.isMedalGame(),
        document.isExtraGame(),
        settings(document.getSettings()),
        scores,
        penalties,
        list(document.getAssignments(), KumiteGameDocumentMapper::assignment),
        list(document.getClockActions(), KumiteGameDocumentMapper::clockAction),
        list(document.getSenshuRecords(), KumiteGameDocumentMapper::senshuRecord),
        list(document.getAmendments(), KumiteGameDocumentMapper::amendment),
        list(document.getResults(), KumiteGameDocumentMapper::result));
  }

  /**
   * A game not yet stored. Its id is already given, so the insert keeps it; the version comes from
   * the insert, and the instants from auditing.
   */
  public static KumiteGameDocument toNewDocument(KumiteGame game) {
    return document(game, 0, null, null);
  }

  /**
   * A change to a stored game. The domain gives the fields it owns; {@code stored} gives the
   * version the write is checked against and the created instant, which the domain never sees.
   */
  public static KumiteGameDocument toDocument(KumiteGame game, KumiteGameDocument stored) {
    if (!game.id().equals(stored.getId())) {
      throw new IllegalArgumentException(
          "Game " + game.id() + " cannot be written onto game " + stored.getId());
    }
    return document(game, stored.getVersion(), stored.getCreatedAt(), stored.getModifiedAt());
  }

  private static KumiteGameDocument document(
      KumiteGame game, long version, Instant createdAt, Instant modifiedAt) {
    Map<String, StoredSide> sides = new LinkedHashMap<>();
    for (Side side : Side.values()) {
      sides.put(
          side.name(),
          new StoredSide(
              game.scores(side).stream().map(KumiteGameDocumentMapper::storedScore).toList(),
              game.penalties(side).stream().map(KumiteGameDocumentMapper::storedPenalty).toList()));
    }
    return new KumiteGameDocument(
        game.id(),
        version,
        KumiteGameDocument.CURRENT_SCHEMA_VERSION,
        game.format().name(),
        game.isMedalGame(),
        game.isExtraGame(),
        storedSettings(game.settings()),
        sides,
        game.assignments().stream().map(KumiteGameDocumentMapper::storedAssignment).toList(),
        game.clockActions().stream().map(KumiteGameDocumentMapper::storedClockAction).toList(),
        game.senshuRecords().stream().map(KumiteGameDocumentMapper::storedSenshuRecord).toList(),
        game.amendments().stream().map(KumiteGameDocumentMapper::storedAmendment).toList(),
        game.results().stream().map(KumiteGameDocumentMapper::storedResult).toList(),
        createdAt,
        modifiedAt);
  }

  private static Settings settings(StoredSettings stored) {
    Map<ScoreLevel, Integer> points = new EnumMap<>(ScoreLevel.class);
    stored.pointValues().forEach((level, value) -> points.put(ScoreLevel.valueOf(level), value));
    return new Settings(
        stored.durationMs(),
        orEmpty(stored.durationsOfferedMs()),
        points,
        stored.leadToWin(),
        stored.finalWindowMs(),
        stored.lateScoreWindowMs(),
        stored.chuiLimit(),
        stored.roundRobinForfeitPoints(),
        orEmpty(stored.addTimeStepsMs()),
        stored.medicalTimeMs(),
        stored.timeShownToleranceMs(),
        stored.rulebookVersion());
  }

  private static StoredSettings storedSettings(Settings settings) {
    Map<String, Integer> points = new LinkedHashMap<>();
    for (ScoreLevel level : ScoreLevel.values()) {
      points.put(level.name(), settings.pointsFor(level));
    }
    return new StoredSettings(
        settings.durationMs(),
        settings.durationsOfferedMs(),
        points,
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

  private static EntryMeta meta(StoredMeta stored) {
    return new EntryMeta(
        stored.entryId(),
        stored.order(),
        stored.recordedAt(),
        stored.recordedBy(),
        list(stored.raisedWarnings(), Warning::valueOf));
  }

  private static StoredMeta storedMeta(EntryMeta meta) {
    return new StoredMeta(
        meta.id(),
        meta.order(),
        meta.recordedAt(),
        meta.recordedBy(),
        meta.raisedWarnings().stream().map(Warning::name).toList());
  }

  private static Score score(StoredScore stored) {
    return new Score(
        ScoreLevel.valueOf(stored.level()),
        ScoreSource.valueOf(stored.source()),
        stored.exchange(),
        stored.clockRemainingMs(),
        meta(stored.meta()));
  }

  private static StoredScore storedScore(Score score) {
    return new StoredScore(
        score.level().name(),
        score.source().name(),
        score.exchange(),
        score.clockRemainingMs(),
        storedMeta(score.meta()));
  }

  private static Penalty penalty(StoredPenalty stored) {
    return new Penalty(
        PenaltyLevel.valueOf(stored.level()),
        parse(Infraction.class, stored.infraction()),
        stored.exchange(),
        stored.clockRemainingMs(),
        meta(stored.meta()));
  }

  private static StoredPenalty storedPenalty(Penalty penalty) {
    return new StoredPenalty(
        penalty.level().name(),
        name(penalty.infraction()),
        penalty.exchange(),
        penalty.clockRemainingMs(),
        storedMeta(penalty.meta()));
  }

  private static Assignment assignment(StoredAssignment stored) {
    return new Assignment(
        AssignmentKind.valueOf(stored.kind()),
        parse(Side.class, stored.side()),
        stored.athleteId(),
        stored.refereeId(),
        parse(PanelRole.class, stored.role()),
        meta(stored.meta()));
  }

  private static StoredAssignment storedAssignment(Assignment assignment) {
    return new StoredAssignment(
        assignment.kind().name(),
        name(assignment.side()),
        assignment.athleteId(),
        assignment.refereeId(),
        name(assignment.role()),
        storedMeta(assignment.meta()));
  }

  private static ClockAction clockAction(StoredClockAction stored) {
    return new ClockAction(
        Timer.valueOf(stored.timer()),
        parse(Side.class, stored.side()),
        ClockActionKind.valueOf(stored.action()),
        stored.timeShownMs(),
        stored.amountMs(),
        parse(PauseReason.class, stored.pauseReason()),
        meta(stored.meta()));
  }

  private static StoredClockAction storedClockAction(ClockAction action) {
    return new StoredClockAction(
        action.timer().name(),
        name(action.side()),
        action.action().name(),
        action.timeShownMs(),
        action.amountMs(),
        name(action.pauseReason()),
        storedMeta(action.meta()));
  }

  private static SenshuRecord senshuRecord(StoredSenshuRecord stored) {
    return new SenshuRecord(
        Side.valueOf(stored.side()),
        SenshuRecordKind.valueOf(stored.kind()),
        stored.reason(),
        stored.exchange(),
        stored.clockRemainingMs(),
        meta(stored.meta()));
  }

  private static StoredSenshuRecord storedSenshuRecord(SenshuRecord record) {
    return new StoredSenshuRecord(
        record.side().name(),
        record.kind().name(),
        record.reason(),
        record.exchange(),
        record.clockRemainingMs(),
        storedMeta(record.meta()));
  }

  private static Amendment amendment(StoredAmendment stored) {
    return new Amendment(
        AmendmentKind.valueOf(stored.kind()),
        stored.targetId(),
        stored.reason(),
        meta(stored.meta()));
  }

  private static StoredAmendment storedAmendment(Amendment amendment) {
    return new StoredAmendment(
        amendment.kind().name(),
        amendment.targetId(),
        amendment.reason(),
        storedMeta(amendment.meta()));
  }

  private static Result result(StoredResult stored) {
    return new Result(
        Outcome.valueOf(stored.outcome()),
        parse(Side.class, stored.winner()),
        ResultReason.valueOf(stored.reason()),
        stored.reasonDetail(),
        stored.bothDisqualified(),
        meta(stored.meta()));
  }

  private static StoredResult storedResult(Result result) {
    return new StoredResult(
        result.outcome().name(),
        name(result.winner()),
        result.reason().name(),
        result.reasonDetail(),
        result.bothDisqualified(),
        storedMeta(result.meta()));
  }

  private static <S, T> List<T> list(List<S> stored, Function<S, T> read) {
    return orEmpty(stored).stream().map(read).toList();
  }

  private static <T> List<T> orEmpty(List<T> stored) {
    return stored == null ? List.of() : stored;
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String name) {
    return name == null ? null : Enum.valueOf(type, name);
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }
}

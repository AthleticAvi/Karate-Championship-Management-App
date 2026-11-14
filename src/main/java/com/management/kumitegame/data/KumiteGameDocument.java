package com.management.kumitegame.data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A game as stored: one document per game, every entry embedded, so each action is one atomic
 * write.
 *
 * <p>Knows nothing of the rules. {@link KumiteGameDocumentMapper} turns it into the domain type and
 * back. Values the domain holds as enums are stored as their names, as plain strings, so a name
 * this build no longer knows reaches the mapper, the one place an old shape is absorbed, rather
 * than failing inside the conversion. Durations are whole milliseconds.
 *
 * <p>Nothing derived is stored: status, clocks, totals and the current result are folded from the
 * entries each time the game is read.
 */
@Document("kumiteGame")
public class KumiteGameDocument {

  /**
   * The shape this class writes. Raise it when a field is added, renamed, moved or changes meaning:
   * a build that does not know a field would drop it on its next save, so it must refuse the shape
   * instead.
   *
   * <ul>
   *   <li>1: the first shape.
   *   <li>2: adds {@code senshuRecords}. A shape 1 game reads with none.
   *   <li>3: adds {@code statusChanges}. A shape 2 game reads with none.
   *   <li>4: adds {@code suggested} to each result. A shape 3 result reads with none.
   *   <li>5: adds {@code panelLimit} to the settings. A shape 4 game reads with none and is saved
   *       with none: it keeps no limit, as it was created.
   * </ul>
   */
  public static final int CURRENT_SCHEMA_VERSION = 5;

  @Id private String id;

  /** Primitive, so an unsaved document reads as new: see {@code [optimistic-locking]}. */
  @Version private long version;

  private int schemaVersion;
  private String format;
  private boolean medalGame;
  private boolean extraGame;
  private StoredSettings settings;
  private Map<String, StoredSide> sides;
  private List<StoredAssignment> assignments;
  private List<StoredClockAction> clockActions;
  private List<StoredSenshuRecord> senshuRecords;
  private List<StoredAmendment> amendments;
  private List<StoredResult> results;
  private List<StoredStatusChange> statusChanges;
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant modifiedAt;

  /** The creator Spring Data rebuilds a stored game with, and the mapper writes through. */
  @PersistenceCreator
  public KumiteGameDocument(
      String id,
      long version,
      int schemaVersion,
      String format,
      boolean medalGame,
      boolean extraGame,
      StoredSettings settings,
      Map<String, StoredSide> sides,
      List<StoredAssignment> assignments,
      List<StoredClockAction> clockActions,
      List<StoredSenshuRecord> senshuRecords,
      List<StoredAmendment> amendments,
      List<StoredResult> results,
      List<StoredStatusChange> statusChanges,
      Instant createdAt,
      Instant modifiedAt) {
    this.id = id;
    this.version = version;
    this.schemaVersion = schemaVersion;
    this.format = format;
    this.medalGame = medalGame;
    this.extraGame = extraGame;
    this.settings = settings;
    this.sides = sides;
    this.assignments = assignments;
    this.clockActions = clockActions;
    this.senshuRecords = senshuRecords;
    this.amendments = amendments;
    this.results = results;
    this.statusChanges = statusChanges;
    this.createdAt = createdAt;
    this.modifiedAt = modifiedAt;
  }

  /**
   * What every entry carries.
   *
   * <p>The client's id is stored as {@code entryId}, not {@code id}: Spring Data maps a property
   * named {@code id} to {@code _id}, in a nested document too.
   */
  public record StoredMeta(
      String entryId,
      int order,
      Instant recordedAt,
      String recordedBy,
      List<String> raisedWarnings) {}

  /** The rule values the game runs under. {@code panelLimit} is absent before shape 5. */
  public record StoredSettings(
      long durationMs,
      List<Long> durationsOfferedMs,
      Map<String, Integer> pointValues,
      int leadToWin,
      long finalWindowMs,
      long lateScoreWindowMs,
      int chuiLimit,
      int roundRobinForfeitPoints,
      List<Long> addTimeStepsMs,
      long medicalTimeMs,
      long timeShownToleranceMs,
      Integer panelLimit,
      String rulebookVersion) {}

  /** One side's scores and penalties, keyed by the side's name in {@code sides}. */
  public record StoredSide(List<StoredScore> scores, List<StoredPenalty> penalties) {}

  public record StoredScore(
      String level, String source, Integer exchange, Long clockRemainingMs, StoredMeta meta) {}

  public record StoredPenalty(
      String level, String infraction, Integer exchange, Long clockRemainingMs, StoredMeta meta) {}

  public record StoredAssignment(
      String kind, String side, String athleteId, String refereeId, String role, StoredMeta meta) {}

  public record StoredClockAction(
      String timer,
      String side,
      String action,
      long timeShownMs,
      Long amountMs,
      String pauseReason,
      StoredMeta meta) {}

  public record StoredSenshuRecord(
      String side,
      String kind,
      String reason,
      Integer exchange,
      Long clockRemainingMs,
      StoredMeta meta) {}

  public record StoredAmendment(String kind, String targetId, String reason, StoredMeta meta) {}

  /**
   * A result as given.
   *
   * @param suggested what the rules suggested when it was recorded; absent when they suggested
   *     nothing, and in a shape 3 game
   */
  public record StoredResult(
      String outcome,
      String winner,
      String reason,
      String reasonDetail,
      boolean bothDisqualified,
      StoredSuggestedResult suggested,
      StoredMeta meta) {}

  /** A suggested result, kept on the result it was recorded against. */
  public record StoredSuggestedResult(
      String outcome, String winner, String reason, boolean bothDisqualified) {}

  public record StoredStatusChange(String kind, String reason, StoredMeta meta) {}

  public String getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public String getFormat() {
    return format;
  }

  public boolean isMedalGame() {
    return medalGame;
  }

  public boolean isExtraGame() {
    return extraGame;
  }

  public StoredSettings getSettings() {
    return settings;
  }

  public Map<String, StoredSide> getSides() {
    return sides;
  }

  public List<StoredAssignment> getAssignments() {
    return assignments;
  }

  public List<StoredClockAction> getClockActions() {
    return clockActions;
  }

  public List<StoredSenshuRecord> getSenshuRecords() {
    return senshuRecords;
  }

  public List<StoredAmendment> getAmendments() {
    return amendments;
  }

  public List<StoredResult> getResults() {
    return results;
  }

  public List<StoredStatusChange> getStatusChanges() {
    return statusChanges;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getModifiedAt() {
    return modifiedAt;
  }
}

package com.management.athlete.data;

import com.management.athlete.domain.Athlete;

/**
 * Turns a stored athlete into the domain type, and the domain type back into what is stored.
 *
 * <p>The one place an old stored shape is absorbed: when {@link
 * AthleteDocument#CURRENT_SCHEMA_VERSION} rises, {@link #toDomain} reads every older shape, and
 * every write stores the current one. A shape newer than this build knows is refused, never guessed
 * at, such as after a rollback.
 */
public final class AthleteDocumentMapper {

  private AthleteDocumentMapper() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  public static Athlete toDomain(AthleteDocument document) {
    if (document.getSchemaVersion() > AthleteDocument.CURRENT_SCHEMA_VERSION) {
      throw new IllegalStateException(
          "Athlete "
              + document.getId()
              + " is stored at schema version "
              + document.getSchemaVersion()
              + ", newer than this build reads");
    }
    return new Athlete(document.getId(), document.getName(), document.getWeightKg());
  }

  /**
   * An athlete not yet stored. The insert assigns the id and the version, and auditing the
   * instants.
   */
  public static AthleteDocument toNewDocument(String name, Double weightKg) {
    return new AthleteDocument(
        null, 0, AthleteDocument.CURRENT_SCHEMA_VERSION, name, weightKg, null, null);
  }

  /**
   * A change to a stored athlete. The domain gives the fields it owns; {@code stored} gives the
   * version the write is checked against and the created instant, which the domain never sees.
   */
  public static AthleteDocument toDocument(Athlete athlete, AthleteDocument stored) {
    if (!athlete.id().equals(stored.getId())) {
      throw new IllegalArgumentException(
          "Athlete " + athlete.id() + " cannot be written onto athlete " + stored.getId());
    }
    return new AthleteDocument(
        athlete.id(),
        stored.getVersion(),
        AthleteDocument.CURRENT_SCHEMA_VERSION,
        athlete.name(),
        athlete.weightKg(),
        stored.getCreatedAt(),
        stored.getModifiedAt());
  }
}

package com.management.referee.data;

import com.management.referee.domain.Referee;

/**
 * Turns a stored referee into the domain type, and the domain type back into what is stored.
 *
 * <p>The one place an old stored shape is absorbed: when {@link
 * RefereeDocument#CURRENT_SCHEMA_VERSION} rises, {@link #toDomain} reads every older shape, and
 * every write stores the current one. A shape newer than this build knows is refused, never guessed
 * at, such as after a rollback.
 */
public final class RefereeDocumentMapper {

  private RefereeDocumentMapper() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  public static Referee toDomain(RefereeDocument document) {
    if (document.getSchemaVersion() > RefereeDocument.CURRENT_SCHEMA_VERSION) {
      throw new IllegalStateException(
          "Referee "
              + document.getId()
              + " is stored at schema version "
              + document.getSchemaVersion()
              + ", newer than this build reads");
    }
    return new Referee(document.getId(), document.getName());
  }

  /**
   * A referee not yet stored. The insert assigns the id and the version, and auditing the instants.
   */
  public static RefereeDocument toNewDocument(String name) {
    return new RefereeDocument(null, 0, RefereeDocument.CURRENT_SCHEMA_VERSION, name, null, null);
  }

  /**
   * A change to a stored referee. The domain gives the fields it owns; {@code stored} gives the
   * version the write is checked against and the created instant, which the domain never sees.
   */
  public static RefereeDocument toDocument(Referee referee, RefereeDocument stored) {
    if (!referee.id().equals(stored.getId())) {
      throw new IllegalArgumentException(
          "Referee " + referee.id() + " cannot be written onto referee " + stored.getId());
    }
    return new RefereeDocument(
        referee.id(),
        stored.getVersion(),
        RefereeDocument.CURRENT_SCHEMA_VERSION,
        referee.name(),
        stored.getCreatedAt(),
        stored.getModifiedAt());
  }
}

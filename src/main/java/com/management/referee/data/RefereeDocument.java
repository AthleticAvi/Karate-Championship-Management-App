package com.management.referee.data;

import com.management.data.IdentityDocument;
import java.time.Instant;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A referee as stored: what every named identity stores, and no field of its own.
 *
 * <p>Knows nothing of the business. {@link RefereeDocumentMapper} turns it into the domain type and
 * back. The stored metadata, {@code version}, {@code schemaVersion} and the two instants, never
 * reaches the domain.
 */
@Document("referee")
public class RefereeDocument extends IdentityDocument {

  /** The shape this class writes: when it rises is stated on {@link IdentityDocument}. */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  /** The creator Spring Data rebuilds a stored referee with, and the mapper writes through. */
  @PersistenceCreator
  public RefereeDocument(
      String id,
      long version,
      int schemaVersion,
      String name,
      Instant createdAt,
      Instant modifiedAt) {
    super(id, version, schemaVersion, name, createdAt, modifiedAt);
  }
}

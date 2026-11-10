package com.management.referee.data;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A referee as stored: the domain fields plus what only storage needs.
 *
 * <p>Knows nothing of the business. {@link RefereeDocumentMapper} turns it into the domain type and
 * back. The stored metadata, {@code version}, {@code schemaVersion} and the two instants, never
 * reaches the domain.
 *
 * <p>A class, like the other stored types: Spring Data writes the id, the version and the instants
 * onto the object it saves. Callers still use what {@code save} returns.
 */
@Document("referee")
public class RefereeDocument {

  /** The shape this class writes. Raise it when a field is renamed, moved or changes meaning. */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  @Id private String id;

  /** Primitive, so an unsaved document reads as new: see {@code [optimistic-locking]}. */
  @Version private long version;

  private int schemaVersion;
  private String name;
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant modifiedAt;

  /** The creator Spring Data rebuilds a stored referee with, and the mapper writes through. */
  @PersistenceCreator
  public RefereeDocument(
      String id,
      long version,
      int schemaVersion,
      String name,
      Instant createdAt,
      Instant modifiedAt) {
    this.id = id;
    this.version = version;
    this.schemaVersion = schemaVersion;
    this.name = name;
    this.createdAt = createdAt;
    this.modifiedAt = modifiedAt;
  }

  public String getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getModifiedAt() {
    return modifiedAt;
  }
}

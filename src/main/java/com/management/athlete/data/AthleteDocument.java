package com.management.athlete.data;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An athlete as stored: the domain fields plus what only storage needs.
 *
 * <p>Knows nothing of the business. {@link AthleteDocumentMapper} turns it into the domain type and
 * back. The stored metadata, {@code version}, {@code schemaVersion} and the two instants, never
 * reaches the domain.
 *
 * <p>A class, like the other stored types: Spring Data writes the id, the version and the instants
 * onto the object it saves. Callers still use what {@code save} returns.
 */
@Document("athlete")
public class AthleteDocument {

  /**
   * The shape this class writes. Raise it when a field is added, renamed, moved or changes meaning:
   * a save replaces the whole document, so a build that does not know a field would drop it. See
   * {@code [schema-versioning]} in {@code
   * context/workflow/databases/mongodb/design/persistence.md}.
   */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  @Id private String id;

  /** Primitive, so an unsaved document reads as new: see {@code [optimistic-locking]}. */
  @Version private long version;

  private int schemaVersion;
  private String name;
  private Double weightKg;
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant modifiedAt;

  /** The creator Spring Data rebuilds a stored athlete with, and the mapper writes through. */
  @PersistenceCreator
  public AthleteDocument(
      String id,
      long version,
      int schemaVersion,
      String name,
      Double weightKg,
      Instant createdAt,
      Instant modifiedAt) {
    this.id = id;
    this.version = version;
    this.schemaVersion = schemaVersion;
    this.name = name;
    this.weightKg = weightKg;
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

  public Double getWeightKg() {
    return weightKg;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getModifiedAt() {
    return modifiedAt;
  }
}

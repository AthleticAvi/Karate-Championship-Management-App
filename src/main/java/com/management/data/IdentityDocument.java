package com.management.data;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;

/**
 * What every stored named identity holds, such as an athlete or a referee: the name, plus what only
 * storage needs. Each feature's document extends it with its own fields, its collection and its
 * {@code CURRENT_SCHEMA_VERSION}.
 *
 * <p><strong>When {@code CURRENT_SCHEMA_VERSION} rises.</strong> Each document that extends this
 * one declares the shape it writes. Raise it when a field is added, renamed, moved or changes
 * meaning, in that document or here, where a change raises it in every one: a save replaces the
 * whole document, so a build that does not know a field would drop it. See {@code
 * [schema-versioning]} in {@code context/workflow/databases/mongodb/design/persistence.md}.
 *
 * <p>A class, like the other stored types: Spring Data writes the id, the version and the instants
 * onto the object it saves. Callers still use what {@code save} returns.
 */
public abstract class IdentityDocument {

  @Id private String id;

  /** Primitive, so an unsaved document reads as new: see {@code [optimistic-locking]}. */
  @Version private long version;

  private int schemaVersion;
  private String name;
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant modifiedAt;

  protected IdentityDocument(
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

package com.management.athlete.data;

import com.management.data.IdentityDocument;
import java.time.Instant;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An athlete as stored: what every named identity stores, plus the weight.
 *
 * <p>Knows nothing of the business. {@link AthleteDocumentMapper} turns it into the domain type and
 * back. The stored metadata, {@code version}, {@code schemaVersion} and the two instants, never
 * reaches the domain.
 */
@Document("athlete")
public class AthleteDocument extends IdentityDocument {

  /** The shape this class writes: when it rises is stated on {@link IdentityDocument}. */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  private Double weightKg;

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
    super(id, version, schemaVersion, name, createdAt, modifiedAt);
    this.weightKg = weightKg;
  }

  public Double getWeightKg() {
    return weightKg;
  }
}

package com.management.referee.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.referee.domain.Referee;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefereeDocumentMapperTest {

  private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
  private static final Instant MODIFIED = Instant.parse("2026-01-02T08:30:00.123Z");

  /** Every field differs from its default, so a field the mapper drops cannot pass unseen. */
  private static RefereeDocument stored() {
    return new RefereeDocument(
        "referee-1", 7, RefereeDocument.CURRENT_SCHEMA_VERSION, "Sato", CREATED, MODIFIED);
  }

  @Test
  void documentToDomainToDocument_keepsEveryField() {
    RefereeDocument stored = stored();

    RefereeDocument written =
        RefereeDocumentMapper.toDocument(RefereeDocumentMapper.toDomain(stored), stored);

    assertThat(written).usingRecursiveComparison().isEqualTo(stored);
    assertThat(written).hasNoNullFieldsOrProperties();
  }

  @Test
  void toDomain_carriesOnlyWhatTheBusinessOwns() {
    assertThat(RefereeDocumentMapper.toDomain(stored()))
        .isEqualTo(new Referee("referee-1", "Sato"));
  }

  @Test
  void rename_changesTheNameAndKeepsTheStoredMetadata() {
    RefereeDocument stored = stored();

    RefereeDocument written =
        RefereeDocumentMapper.toDocument(
            RefereeDocumentMapper.toDomain(stored).rename("Tanaka"), stored);

    assertThat(written.getName()).isEqualTo("Tanaka");
    assertThat(written).usingRecursiveComparison().ignoringFields("name").isEqualTo(stored);
  }

  @Test
  void newDocument_isUnsavedAtTheCurrentShape() {
    RefereeDocument written = RefereeDocumentMapper.toNewDocument(Referee.named("Sato"));

    assertThat(written.getId()).isNull();
    assertThat(written.getVersion()).isZero();
    assertThat(written.getSchemaVersion()).isEqualTo(1);
    assertThat(written.getName()).isEqualTo("Sato");
    assertThat(written.getCreatedAt()).isNull();
    assertThat(written.getModifiedAt()).isNull();
  }

  @Test
  void domainToDocumentToDomain_keepsEveryField() {
    Referee referee = new Referee("referee-1", "Sato");

    assertThat(RefereeDocumentMapper.toDomain(RefereeDocumentMapper.toDocument(referee, stored())))
        .isEqualTo(referee);
  }

  /** Written by a newer build: refused rather than read as a shape it is not. */
  @Test
  void toDomain_refusesShapesNewerThanThisBuild() {
    RefereeDocument newer =
        new RefereeDocument(
            "referee-1", 7, RefereeDocument.CURRENT_SCHEMA_VERSION + 1, "Sato", CREATED, MODIFIED);

    assertThatThrownBy(() -> RefereeDocumentMapper.toDomain(newer))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("schema version 2");
  }
}

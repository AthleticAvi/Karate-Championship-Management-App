package com.management.athlete.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.athlete.domain.Athlete;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AthleteDocumentMapperTest {

  private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
  private static final Instant MODIFIED = Instant.parse("2026-01-02T08:30:00.123Z");

  /** Every field differs from its default, so a field the mapper drops cannot pass unseen. */
  private static AthleteDocument stored() {
    return new AthleteDocument(
        "athlete-1", 7, AthleteDocument.CURRENT_SCHEMA_VERSION, "Kenji", 61.5, CREATED, MODIFIED);
  }

  @Test
  void documentToDomainToDocument_keepsEveryField() {
    AthleteDocument stored = stored();

    AthleteDocument written =
        AthleteDocumentMapper.toDocument(AthleteDocumentMapper.toDomain(stored), stored);

    assertThat(written).usingRecursiveComparison().isEqualTo(stored);
    assertThat(written).hasNoNullFieldsOrProperties();
  }

  @Test
  void toDomain_carriesOnlyWhatTheBusinessOwns() {
    assertThat(AthleteDocumentMapper.toDomain(stored()))
        .isEqualTo(new Athlete("athlete-1", "Kenji", 61.5));
  }

  @Test
  void update_changesNameAndWeight_andKeepsTheStoredMetadata() {
    AthleteDocument stored = stored();

    AthleteDocument written =
        AthleteDocumentMapper.toDocument(
            AthleteDocumentMapper.toDomain(stored).update("Kenta", 67.0), stored);

    assertThat(written.getName()).isEqualTo("Kenta");
    assertThat(written.getWeightKg()).isEqualTo(67.0);
    assertThat(written)
        .usingRecursiveComparison()
        .ignoringFields("name", "weightKg")
        .isEqualTo(stored);
  }

  @Test
  void newDocument_isUnsavedAtTheCurrentShape() {
    AthleteDocument written = AthleteDocumentMapper.toNewDocument("Kenji", null);

    assertThat(written.getId()).isNull();
    assertThat(written.getVersion()).isZero();
    assertThat(written.getSchemaVersion()).isEqualTo(1);
    assertThat(written.getWeightKg()).isNull();
    assertThat(written.getCreatedAt()).isNull();
  }

  @Test
  void toDocument_refusesAnotherAthletesStoredDocument() {
    assertThatThrownBy(() ->
            AthleteDocumentMapper.toDocument(new Athlete("athlete-2", "Kenji", null), stored()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toDomain_refusesShapesNewerThanThisBuild() {
    AthleteDocument newer =
        new AthleteDocument(
            "athlete-1",
            7,
            AthleteDocument.CURRENT_SCHEMA_VERSION + 1,
            "Kenji",
            null,
            CREATED,
            MODIFIED);

    assertThatThrownBy(() -> AthleteDocumentMapper.toDomain(newer))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("schema version 2");
  }
}

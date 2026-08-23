package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.enums.PlayerColor;
import com.management.models.converters.LegacyWinnerConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every form the {@code winner} field has ever been stored in, read back.
 *
 * <p>Unit level: the conversion is pure, so proving it needs no database. That the mapping layer
 * actually applies it to the property is a separate question, asserted against a real MongoDB in
 * {@link LegacyWinnerIT} — a converter that is correct but never invoked would pass this class and
 * still leave every legacy match unreadable.
 */
class LegacyWinnerConverterTest {

  private final LegacyWinnerConverter converter = new LegacyWinnerConverter();

  @Test
  void read_whenTheValueIsTheLegacyPlaceholder_isNoWinner() {
    assertThat(converter.read("Pending game ending", null))
        .as("the placeholder meant 'not decided', which is null")
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"RED player: Kenji", "RED player: ", "RED"})
  void read_whenTheValueIsLegacyRedSentence_isRed(String stored) {
    assertThat(converter.read(stored, null)).isEqualTo(PlayerColor.RED);
  }

  @Test
  void read_whenTheValueIsLegacyBlueSentence_isBlue() {
    assertThat(converter.read("BLUE player: Sato", null)).isEqualTo(PlayerColor.BLUE);
  }

  @Test
  void read_whenTheValueIsTheCurrentForm_isThatColour() {
    assertThat(converter.read("BLUE", null)).isEqualTo(PlayerColor.BLUE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "purple", "Pending game ending "})
  void read_whenTheValueIsUnusable_isNoWinner(String stored) {
    assertThat(converter.read(stored, null))
        .as("an unreadable winner must not take the whole match down with it")
        .isNull();
  }

  @Test
  void read_whenTheFieldIsAbsent_isNoWinner() {
    assertThat(converter.read(null, null)).isNull();
  }

  @Test
  void write_alwaysEmitsTheEnumName_soDocumentsMigrateOnTheirNextSave() {
    assertThat(converter.write(PlayerColor.RED, null)).isEqualTo("RED");
    assertThat(converter.write(PlayerColor.BLUE, null)).isEqualTo("BLUE");
    assertThat(converter.write(null, null)).isNull();
  }
}

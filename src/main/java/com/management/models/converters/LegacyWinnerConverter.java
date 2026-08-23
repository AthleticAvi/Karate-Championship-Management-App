package com.management.models.converters;

import com.management.enums.PlayerColor;
import java.util.Arrays;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;

/**
 * Reads the {@code winner} field, whatever era of this application wrote it.
 *
 * <p><strong>Why this exists.</strong> Before #34 the winner was a display sentence: a new match
 * started life holding the literal {@code "Pending game ending"}, and a decided one held {@code
 * "RED player: Kenji"}. #34 made the field a {@link PlayerColor}, which is the right shape — but
 * every document already in the database still holds a string. Spring Data converts a stored string
 * to an enum with {@code Enum.valueOf}, so without this converter loading any pre-#34 match fails
 * with {@code No enum constant com.management.enums.PlayerColor.Pending game ending}, and that
 * failure takes out {@code GET /api/kumitegame/{id}} and every scoring endpoint for that match
 * permanently.
 *
 * <p><strong>Why a property converter rather than a {@code Converter<String,
 * PlayerColor>}.</strong> A converter registered for the type pair applies to <em>every</em>
 * string-to-colour conversion in the mapping layer, which includes the keys of {@code playersMap}.
 * This one is bound to a single property by {@code @ValueConverter}, so it cannot affect anything
 * else — the narrowest scope the framework offers for exactly this job.
 *
 * <p><strong>What it does not do.</strong> It does not make the legacy form writable. {@link
 * #write} always emits the enum name, so every document this application saves is migrated on its
 * next write and the legacy branch below becomes progressively dead. When no legacy documents
 * remain, delete this class and the annotation with it.
 */
public class LegacyWinnerConverter
    implements PropertyValueConverter<
        PlayerColor, String, ValueConversionContext<MongoPersistentProperty>> {

  private static final Logger log = LoggerFactory.getLogger(LegacyWinnerConverter.class);

  /** The placeholder a pre-#34 match was constructed with. Means "not decided" — that is null. */
  private static final String LEGACY_PLACEHOLDER = "Pending game ending";

  @Override
  public @Nullable PlayerColor read(
      @Nullable String value, ValueConversionContext<MongoPersistentProperty> context) {

    if (value == null || value.isBlank() || LEGACY_PLACEHOLDER.equalsIgnoreCase(value.trim())) {
      return null;
    }

    String stored = value.trim();

    // The current form, and the only one this application writes.
    PlayerColor exact = matchExactly(stored);
    if (exact != null) {
      return exact;
    }

    // The legacy form: "RED player: Kenji". The colour is the first token; the name that follows
    // is discarded deliberately, since it was never this field's to hold.
    PlayerColor prefixed = matchLeadingColour(stored);
    if (prefixed != null) {
      log.info(
          "Read a pre-#34 winner value {} as {}. It will be stored as the colour on the next save.",
          value,
          prefixed);
      return prefixed;
    }

    // Not a colour in any form this application ever wrote. Treating it as "undecided" loses
    // nothing a colour would have carried, and is far better than failing the whole read.
    log.warn("Unrecognised winner value {} read as no winner.", value);
    return null;
  }

  @Override
  public @Nullable String write(
      @Nullable PlayerColor value, ValueConversionContext<MongoPersistentProperty> context) {
    return value == null ? null : value.name();
  }

  private static @Nullable PlayerColor matchExactly(String stored) {
    return Arrays.stream(PlayerColor.values())
        .filter(color -> color.name().equalsIgnoreCase(stored))
        .findFirst()
        .orElse(null);
  }

  private static @Nullable PlayerColor matchLeadingColour(String stored) {
    String upper = stored.toUpperCase(Locale.ROOT);
    return Arrays.stream(PlayerColor.values())
        .filter(color -> upper.startsWith(color.name()))
        .findFirst()
        .orElse(null);
  }
}

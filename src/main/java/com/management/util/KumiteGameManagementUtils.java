package com.management.util;

import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.exceptions.InvalidPlayerColorException;
import com.management.exceptions.PointTypeNotFoundException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public class KumiteGameManagementUtils {

  private KumiteGameManagementUtils() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  private static final Logger log = LoggerFactory.getLogger(KumiteGameManagementUtils.class);

  /**
   * Parses a colour from the URL, or rejects it as bad input.
   *
   * <p>Threw {@link com.management.exceptions.PlayerNotFoundException} until #36, which meant
   * {@code color=purple} came back as a 404 saying no such player — the wrong status and a
   * misleading message for what is simply an unparseable value. {@link InvalidPlayerColorException}
   * had no throw site anywhere in the codebase before this; it does now.
   *
   * <p>The two failures are genuinely different and stay different types: this one is "not a
   * colour", 400. "A real colour that this match does not field" is {@link
   * com.management.exceptions.PlayerNotFoundException}, 404, raised where the match is actually
   * consulted.
   */
  public static PlayerColor mapPlayerColor(String color) {
    return parse(PlayerColor.class, color)
        .orElseThrow(() -> {
          log.debug("KumiteGameManagementUtils - mapPlayerColor - could not map color: {}", color);
          return new InvalidPlayerColorException(
              "Unknown player colour: "
                  + color
                  + ". Expected one of "
                  + names(PlayerColor.values()));
        });
  }

  public static PointsType mapPointToPointType(String pointType) {
    return parse(PointsType.class, pointType)
        .orElseThrow(() -> {
          log.debug(
              "KumiteGameManagementUtils - mapPointToPointType - could not map point: {}",
              pointType);
          return new PointTypeNotFoundException(
              "Unknown point type: "
                  + pointType
                  + ". Expected one of "
                  + names(PointsType.values()));
        });
  }

  /**
   * Renders the accepted values of an enum for an error message.
   *
   * <p>Derived from {@code values()} rather than written out. The hardcoded list this replaces said
   * "IPPON, WAZARI, YOKO" and was already wrong the moment #102 renamed YOKO to YUKO — a message
   * that tells the caller to send a value the server rejects is worse than no message at all.
   */
  private static String names(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", ", "", "."));
  }

  /** The one place text becomes an enum constant: ignores case, in every default locale. */
  private static <E extends Enum<E>> Optional<E> parse(Class<E> type, String value) {
    try {
      return Optional.of(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}

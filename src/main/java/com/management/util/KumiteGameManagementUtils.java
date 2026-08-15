package com.management.util;

import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.exceptions.InvalidPlayerColorException;
import com.management.exceptions.PointTypeNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KumiteGameManagementUtils {

  private KumiteGameManagementUtils() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  private static final Logger log = LoggerFactory.getLogger(KumiteGameManagementUtils.class);

  /**
   * Parses a colour from the URL, or rejects it as bad input.
   *
   * <p>Threw {@link PlayerNotFoundException} until #36, which meant {@code color=purple} came back
   * as a 404 saying no such player — the wrong status and a misleading message for what is simply
   * an unparseable value. {@link InvalidPlayerColorException} had no throw site anywhere in the
   * codebase before this; it does now.
   *
   * <p>The two failures are genuinely different and stay different types: this one is "not a
   * colour", 400. "A real colour that this match does not field" is {@link
   * PlayerNotFoundException}, 404, raised where the match is actually consulted.
   */
  public static PlayerColor mapPlayerColor(String color) {
    if (!(isInPlayerColor(color))) {
      log.debug("KumiteGameManagementUtils - mapPlayerColor - could not map color: {}", color);
      throw new InvalidPlayerColorException(
          "Unknown player colour: " + color + ". Expected one of RED, BLUE.");
    }
    return PlayerColor.valueOf(color.toUpperCase());
  }

  public static boolean isInPlayerColor(String color) {
    try {
      PlayerColor.valueOf(color.toUpperCase());
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public static PointsType mapPointToPointType(String pointType) {
    if (!(isInPointsType(pointType))) {
      log.debug(
          "KumiteGameManagementUtils - mapPointToPointType - could not map point: {}", pointType);
      throw new PointTypeNotFoundException(
          "Unknown point type: " + pointType + ". Expected one of IPPON, WAZARI, YOKO.");
    }
    return PointsType.valueOf(pointType.toUpperCase());
  }

  public static boolean isInPointsType(String pointType) {
    try {
      PointsType.valueOf(pointType.toUpperCase());
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}

package com.management.util;

import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.exceptions.PlayerNotFoundException;
import com.management.exceptions.PointTypeNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KumiteGameManagementUtils {

    private KumiteGameManagementUtils() {
        throw new IllegalStateException("Utility class cannot be instantiated");
    }
    private static final Logger logger = LoggerFactory.getLogger(KumiteGameManagementUtils.class);
    public static PlayerColor mapPlayerColor(String color){
        if (!(isInPlayerColor(color))){
            logger.error("KumiteGameManagementUtils - mapPlayerColor - could not map color: {}", color);
            throw new PlayerNotFoundException("No player associated with the color " + color + " in this game.");
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
        if (!(isInPointsType(pointType))){
            logger.error("KumiteGameManagementUtils - mapPointToPointType - could not map point: {}", pointType);
            throw new PointTypeNotFoundException("No point associated with the point " + pointType + " in points.");
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

package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.exceptions.InvalidPlayerColorException;
import com.management.exceptions.PointTypeNotFoundException;
import com.management.util.KumiteGameManagementUtils;
import org.junit.jupiter.api.Test;

/**
 * The parsing layer, where the 400-versus-404 distinction originates.
 *
 * <p>The status codes themselves are asserted at the web slice; what is asserted here is that the
 * right <em>exception type</em> is raised, since the type is what carries the meaning. A message
 * cannot be mapped to a status.
 */
class KumiteGameManagementUtilsTest {

  @Test
  void mapPlayerColor_whenTheValueIsRecognised_parsesItCaseInsensitively() {
    assertThat(KumiteGameManagementUtils.mapPlayerColor("red")).isEqualTo(PlayerColor.RED);
    assertThat(KumiteGameManagementUtils.mapPlayerColor("BLUE")).isEqualTo(PlayerColor.BLUE);
  }

  @Test
  void mapPlayerColor_whenTheValueIsUnrecognised_throwsInvalidPlayerColor() {
    assertThatThrownBy(() -> KumiteGameManagementUtils.mapPlayerColor("purple"))
        .as("not a colour is bad input (400), not a missing player (404)")
        .isInstanceOf(InvalidPlayerColorException.class)
        .hasMessageContaining("purple");
  }

  @Test
  void mapPointToPointType_whenTheValueIsRecognised_parsesItCaseInsensitively() {
    assertThat(KumiteGameManagementUtils.mapPointToPointType("ippon")).isEqualTo(PointsType.IPPON);
  }

  @Test
  void mapPointToPointType_whenTheValueIsUnrecognised_throwsPointTypeNotFound() {
    assertThatThrownBy(() -> KumiteGameManagementUtils.mapPointToPointType("triple-axel"))
        .isInstanceOf(PointTypeNotFoundException.class);
  }
}

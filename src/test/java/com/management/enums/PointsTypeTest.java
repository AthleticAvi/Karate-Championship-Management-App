package com.management.enums;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.models.Points;
import org.junit.jupiter.api.Test;

class PointsTypeTest {

  @Test
  void addPoint_whenYuko_incrementsScoreByOne() {
    Points points = new Points();
    PointsType.YUKO.getStrategy().addPoint(points);
    assertThat(points.getNumOfPoints()).isEqualTo(1);
  }

  @Test
  void removePoint_whenYuko_decrementsScoreByOneToZeroFloor() {
    Points points = new Points();
    points.setNumOfPoints(1);
    PointsType.YUKO.getStrategy().removePoint(points);
    assertThat(points.getNumOfPoints()).isEqualTo(0);

    PointsType.YUKO.getStrategy().removePoint(points);
    assertThat(points.getNumOfPoints()).isEqualTo(0);
  }
}

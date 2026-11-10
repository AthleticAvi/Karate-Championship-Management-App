package com.management.athlete.domain.strategies;

import com.management.athlete.domain.Points;

public class YukoStrategy implements PointStrategy {
  @Override
  public void addPoint(Points point) {
    point.setNumOfPoints(point.getNumOfPoints() + 1);
  }

  @Override
  public void removePoint(Points point) {
    point.setNumOfPoints(Math.max(point.getNumOfPoints() - 1, 0));
  }
}

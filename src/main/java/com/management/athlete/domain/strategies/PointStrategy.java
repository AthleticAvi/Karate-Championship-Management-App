package com.management.athlete.domain.strategies;

import com.management.athlete.domain.Points;

public interface PointStrategy {
  void addPoint(Points point);

  void removePoint(Points point);
}

package com.management.athlete.domain;

import com.management.athlete.domain.strategies.IpponStrategy;
import com.management.athlete.domain.strategies.PointStrategy;
import com.management.athlete.domain.strategies.WazariStrategy;
import com.management.athlete.domain.strategies.YukoStrategy;

public enum PointsType {
  IPPON(new IpponStrategy()),
  WAZARI(new WazariStrategy()),
  YUKO(new YukoStrategy());

  private final PointStrategy strategy;

  PointsType(PointStrategy strategy) {
    this.strategy = strategy;
  }

  public PointStrategy getStrategy() {
    return strategy;
  }
}

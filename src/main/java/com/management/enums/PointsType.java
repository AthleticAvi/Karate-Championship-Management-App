package com.management.enums;

import com.management.models.strategies.IpponStrategy;
import com.management.models.strategies.PointStrategy;
import com.management.models.strategies.WazariStrategy;
import com.management.models.strategies.YokoStrategy;

public enum PointsType {
    IPPON(new IpponStrategy()),
    WAZARI(new WazariStrategy()),
    YOKO(new YokoStrategy());

    private final PointStrategy strategy;

    PointsType(PointStrategy strategy) {
        this.strategy = strategy;
    }

    public PointStrategy getStrategy() {
        return strategy;
    }
}

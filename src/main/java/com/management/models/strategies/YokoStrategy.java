package com.management.models.strategies;

import com.management.models.Points;

public class YokoStrategy implements PointStrategy {
    @Override
    public void addPoint(Points point) {
        point.setPointsCounter(point.getPointsCounter() + 1);
    }
}

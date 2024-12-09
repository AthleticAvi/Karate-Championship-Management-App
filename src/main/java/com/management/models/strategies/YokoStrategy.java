package com.management.models.strategies;

import com.management.models.Points;

public class YokoStrategy implements PointStrategy {
    @Override
    public void addPoint(Points point) {
        point.setNumOfPoints(point.getNumOfPoints() + 1);
    }
    @Override
    public void removePoint(Points point) { point.setNumOfPoints(Math.max(point.getNumOfPoints() - 1, 0));}
}

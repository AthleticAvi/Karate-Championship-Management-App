package com.management.models;

public class Points {
    private int pointsCounter;

    public Points(){
        this.pointsCounter = 0;
    }

    public int getPointsCounter(){
        return pointsCounter;
    }

    public void setPointsCounter(int pointsCounter) {
        this.pointsCounter = pointsCounter;
    }
}

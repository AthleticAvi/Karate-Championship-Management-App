package com.management.models;

public class Points {
    private int pointsCounter;

    public Points(){
        this.pointsCounter = 0;
    }

    public int getPointsCounter(){
        return pointsCounter;
    }

    public void logIppon(){
        this.pointsCounter += 3;
    }

    public void logWazari(){
        this.pointsCounter += 2;
    }

    public void logYoko(){
        this.pointsCounter += 1;
    }
}

package com.management.models;

public class Foul {
    private int foulCounter;

    public Foul(){
        this.foulCounter = 0;
    }

    public void logFoul(){
        this.foulCounter++;
    }

    public int getFoulCounter(){
        return foulCounter;
    }
}

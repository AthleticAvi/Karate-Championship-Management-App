package com.management.models;

public class Foul {
    private int numOfFouls;

    public Foul(){
        this.numOfFouls = 0;
    }

    public void addFoul(){
        this.setNumOfFouls(this.getNumOfFouls() + 1);
    }

    public void removeFoul() {
        this.setNumOfFouls(Math.max(this.getNumOfFouls() - 1, 0));
    }

    public int getNumOfFouls(){
        return numOfFouls;
    }

    public void setNumOfFouls(int numOfFouls) {
        this.numOfFouls = numOfFouls;
    }
}

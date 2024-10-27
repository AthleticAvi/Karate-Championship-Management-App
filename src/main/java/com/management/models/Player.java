package com.management.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class Player {
    @Id
    private String id;
    private String name;
    private String color;
    private Points numberOfPoints;
    private Foul numberOfFouls;

    public Player(String name, String color){
        this.name = name;
        this.color = color;
        this.numberOfPoints = new Points();
        this.numberOfFouls = new Foul();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Points getPoints() {
        return numberOfPoints;
    }

    public void setPoints(Points points){
        this.numberOfPoints = points;
    }

    public Foul getFouls(){
        return this.numberOfFouls;
    }

    public void setFouls(Foul fouls){
        this.numberOfFouls = fouls;
    }

    public void logIppon() {
        this.numberOfPoints.logIppon();
    }

    public void logWazari() {
        this.numberOfPoints.logWazari();
    }

    public void logYoko() {
        this.numberOfPoints.logYoko();
    }

    public void logFoul() {
        this.numberOfFouls.logFoul();
    }

}

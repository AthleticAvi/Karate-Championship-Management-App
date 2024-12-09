package com.management.models;

import com.management.enums.PointsType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class Player {
    @Id
    private String id;
    private String name;
    private Points points;
    private Foul numberOfFouls;

    public Player() {}
    public Player(String name){
        this.name = name;
        this.points = new Points();
        this.numberOfFouls = new Foul();
    }
    public Player(String id, String name, Points points, Foul fouls) {
        this.id = id;
        this.name = name;
        this.points = points;
        this.numberOfFouls = fouls;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) { this.id = id; }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Points getPoints() {
        return points;
    }

    public void setPoints(Points points){
        this.points = points;
    }

    public Foul getFouls(){
        return this.numberOfFouls;
    }

    public void setFouls(Foul fouls){
        this.numberOfFouls = fouls;
    }

    public void addPoint(PointsType pointType) {
        pointType.getStrategy().addPoint(this.points);
    }
    public void logFoul() {
        this.numberOfFouls.logFoul();
    }

}

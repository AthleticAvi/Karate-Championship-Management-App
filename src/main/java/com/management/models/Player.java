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
    private Foul fouls;

    public Player() {}
    public Player(String name){
        this.name = name;
        this.points = new Points();
        this.fouls = new Foul();
    }
    public Player(String id, String name, Points points, Foul fouls) {
        this.id = id;
        this.name = name;
        this.points = points;
        this.fouls = fouls;
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
        return this.fouls;
    }

    public void setFouls(Foul fouls){
        this.fouls = fouls;
    }

    public void addPoint(PointsType pointType) {
        pointType.getStrategy().addPoint(this.points);
    }

    public void removePoint(PointsType pointType){
        pointType.getStrategy().removePoint(this.points);
    }

    public void addFoul() {
        this.fouls.addFoul();
    }

    public void removeFoul() {
        this.fouls.removeFoul();
    }

}

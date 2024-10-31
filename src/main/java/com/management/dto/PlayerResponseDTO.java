package com.management.dto;

public class PlayerResponseDTO {
    private String id;
    private String name;
    private int points;
    private int fouls;

    public PlayerResponseDTO(String id, String name, int points, int fouls) {
        this.id = id;
        this.name = name;
        this.points = points;
        this.fouls = fouls;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getFouls() {
        return fouls;
    }

    public void setFouls(int fouls) {
        this.fouls = fouls;
    }
}
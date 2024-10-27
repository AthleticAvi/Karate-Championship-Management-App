package com.management.models;

public class Referee {
    private String name;
    private KumiteGame kumiteGame;

    public Referee(String name, KumiteGame kumiteGame) {
        this.name = name;
        this.kumiteGame = kumiteGame;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public KumiteGame getKumiteGame() {
        return kumiteGame;
    }

    public void setKumiteGame(KumiteGame kumiteGame) {
        this.kumiteGame = kumiteGame;
    }
}


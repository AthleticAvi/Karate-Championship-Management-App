package com.management.models;

import java.util.List;
import java.util.Timer;

public class KumiteGame {
    private List<Player> players;
    private Timer timer;
    private List<Referee> referees;
    private String winner;
    private String kumiteGameId;

    public KumiteGame(List<Player> players, Timer timer, List<Referee> referees, String kumiteGameId) {
        this.players = players;
        this.timer = timer;
        this.referees = referees;
        this.kumiteGameId = kumiteGameId;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public Timer getTimer() {
        return timer;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public List<Referee> getReferees() {
        return referees;
    }

    public void setReferees(List<Referee> referees) {
        this.referees = referees;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public String getKumiteGameId() {
        return kumiteGameId;
    }

    public void setKumiteGameId(String kumiteGameId) {
        this.kumiteGameId = kumiteGameId;
    }
}


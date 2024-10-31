package com.management.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
public class KumiteGame {
    @Id
    private String id;
    private List<Player> players;
    private List<Referee> referees;
    private String winner;


    public KumiteGame(List<Player> players, List<Referee> referees){
        this.players = players;
        this.referees = referees;
        this.winner = null;
    }

    public String getId(){
        return id;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
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
}


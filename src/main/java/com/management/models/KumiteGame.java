package com.management.models;

import com.management.enums.PlayerColor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document
public class KumiteGame {
    @Id
    private String id;
    private Map<PlayerColor, Player> playersMap;
    private List<Referee> referees;
    private String winner;


    public KumiteGame(Map<PlayerColor, Player> playersMap, List<Referee> referees){
        this.playersMap = playersMap;
        this.referees = referees;
        this.winner = null;
    }

    public String getId(){
        return id;
    }

    public Map<PlayerColor, Player> getPlayersMap() {
        return playersMap;
    }

    public void setPlayersMap(Map<PlayerColor, Player> playersMap) {
        this.playersMap = playersMap;
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

    public void updatePlayer(PlayerColor color, Player updatedPlayer){
        if (playersMap.containsKey(color)){
            playersMap.put(color, updatedPlayer);
        }
        else {
            throw new IllegalArgumentException("Player color not found in the game");
        }
    }
}
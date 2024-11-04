package com.management.dto;

import java.util.List;

public class KumiteGameResponseDTO {

    private String gameId;
    private String player1;
    private String player2;
    private List<String> referees;

    public KumiteGameResponseDTO(String gameId, String player1, String player2, List<String> referees) {
        this.gameId = gameId;
        this.player1 = player1;
        this.player2 = player2;
        this.referees = referees;
    }

    public String getGameId() {
        return gameId;
    }

    public String getPlayer1() {
        return player1;
    }

    public void setPlayer1(String player1) {
        this.player1 = player1;
    }

    public String getPlayer2() {
        return player2;
    }

    public void setPlayer2(String player2) {
        this.player2 = player2;
    }

    public List<String> getReferees() {
        return referees;
    }

    public void setReferees(List<String> referees) {
        this.referees = referees;
    }
}
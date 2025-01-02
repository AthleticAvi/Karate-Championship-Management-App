package com.management.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import com.management.exceptions.PlayerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document
public class KumiteGame {
    @Id
    private String id;
    private GameState gameState;
    private Map<PlayerColor, Player> playersMap;
    private List<Referee> referees;
    private String winner;
    private LocalDateTime startTime;
    private Duration remainingTime;
    private Duration gameDuration;
    @JsonIgnore
    @Transient
    private GameTimer timer;

    private static final Logger logger = LoggerFactory.getLogger(KumiteGame.class);
    private static final String PLAYER_COLOR_NOT_FOUND = "Player color not found in the game";
    private static final String PLAYER_COLOR = " Player color: ";

    public KumiteGame(Map<PlayerColor, Player> playersMap, List<Referee> referees, Duration gameDuration) {
        this.gameState = GameState.QUEUED;
        this.playersMap = playersMap;
        this.referees = referees;
        this.gameDuration = gameDuration;
        this.remainingTime = gameDuration;
        this.winner = "Pending game ending";
    }

    public void initializeTimer(Duration gameDuration) {
        this.timer = new GameTimer(gameDuration);
    }

    public void updatePlayer(PlayerColor color, Player updatedPlayer){
        if (!(playersMap.containsKey(color))) {
            logger.error("KumiteGame - updatePlayer - {}, {}}: {}", PLAYER_COLOR_NOT_FOUND, PLAYER_COLOR, color);
            throw new PlayerNotFoundException(PLAYER_COLOR_NOT_FOUND + PLAYER_COLOR + color);
        }
        playersMap.put(color, updatedPlayer);
    }

    public void updateWinner(PlayerColor color){
        if (!(playersMap.containsKey(color))){
            logger.error("KumiteGame - updateWinner - {}, {}}: {}", PLAYER_COLOR_NOT_FOUND, PLAYER_COLOR, color);
            throw new PlayerNotFoundException(PLAYER_COLOR_NOT_FOUND + PLAYER_COLOR + color);
        }
        String gameWinner = color.name() + " player: " + playersMap.get(color).getName();
        setWinner(gameWinner);
    }

    public String getId(){
        return id;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
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

    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getStartTime() { return startTime; }
    public Duration getRemainingTime() { return remainingTime; }

    public void setRemainingTime(Duration remainingTime) {
        this.remainingTime = remainingTime;
    }

    public Duration getGameDuration() { return gameDuration; }
    public void setGameDuration(Duration gameDuration) { this.gameDuration = gameDuration; }

    public GameTimer getTimer() {
        return timer;
    }
}
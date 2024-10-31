package com.management.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Document
public class KumiteGame {
    @Id
    private String id;
    private List<Player> players;
    private List<Referee> referees;
    private Duration duration;
    private Instant startTime;
    private Instant endTime;
    private String winner;


    public KumiteGame(List<Player> players, List<Referee> referees, Duration duration){
        this.players = players;
        this.referees = referees;
        this.duration = duration;
        this.startTime = null;
        this.endTime = null;
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

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public void addTime(Duration additionalTime) {
        this.duration = this.duration.plus(additionalTime);
    }

    public boolean isActive(){
        return startTime != null && endTime == null;
    }

    public Duration getRemainingTime(){
        if (startTime != null){
            return duration;
        }
        Instant now = Instant.now();
        Duration elapsedTime = Duration.between(startTime, now);
        return duration.minus(elapsedTime);
    }

}


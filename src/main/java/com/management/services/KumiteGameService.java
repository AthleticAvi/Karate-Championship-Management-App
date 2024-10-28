package com.management.services;

import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class KumiteGameService {

    private static final String GAME_NOT_FOUND = "Game not found!";
    @Autowired
    private KumiteGameRepository kumiteGameRepository;

    public KumiteGame createKumiteGame(List<Player> players, List<Referee> referees, Duration duration){
        KumiteGame game = new KumiteGame(players, referees, duration);
        game.setStartTime(Instant.now());
        return kumiteGameRepository.save(game);
    }

    public Optional<KumiteGame> getKumiteGame(String gameId){
        return kumiteGameRepository.findById(gameId);
    }

    public KumiteGame updateKumiteGame(String gameId, KumiteGame gameDetails){
        KumiteGame game = kumiteGameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException(GAME_NOT_FOUND));

        game.setPlayers(gameDetails.getPlayers());
        game.setReferees(gameDetails.getReferees());
        game.setDuration(gameDetails.getDuration());
        return kumiteGameRepository.save(game);
    }

    public void addTime(String gameId, Duration additionalTime){
        KumiteGame game = kumiteGameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException(GAME_NOT_FOUND));

        game.setDuration(game.getDuration().plus(additionalTime));
        kumiteGameRepository.save(game);
    }

    public void endKumiteGame(String gameId){
        KumiteGame game = kumiteGameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException(GAME_NOT_FOUND));

        game.setEndTime(Instant.now());
        kumiteGameRepository.save(game);
    }
}

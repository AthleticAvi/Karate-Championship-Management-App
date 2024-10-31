package com.management.services;

import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import com.management.repositories.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KumiteGameService {

    private static final String GAME_NOT_FOUND = "Game not found!";
    @Autowired
    private KumiteGameRepository kumiteGameRepository;
    @Autowired
    private PlayerRepository playerRepository;

    public KumiteGame createKumiteGame(List<Player> players, List<Referee> referees){
        KumiteGame game = new KumiteGame(players, referees);
        return kumiteGameRepository.save(game);
    }

    public KumiteGame getKumiteGame(String gameId){
        KumiteGame game = kumiteGameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException(GAME_NOT_FOUND));

        List<Player> populatedPlayers = game.getPlayers().stream()
                .map(player -> playerRepository.findById(player.getId()).orElse(null))
                .collect(Collectors.toList());
        game.setPlayers(populatedPlayers);

        return game;
    }

    public KumiteGame updateKumiteGame(String gameId, KumiteGame gameDetails){
        KumiteGame game = kumiteGameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException(GAME_NOT_FOUND));

        game.setPlayers(gameDetails.getPlayers());
        game.setReferees(gameDetails.getReferees());
        return kumiteGameRepository.save(game);
    }

    public void addTime(String gameId, Duration additionalTime){
        KumiteGame game = kumiteGameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException(GAME_NOT_FOUND));

        kumiteGameRepository.save(game);
    }

    public void endKumiteGame(String gameId){
        KumiteGame game = kumiteGameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException(GAME_NOT_FOUND));

        kumiteGameRepository.save(game);
    }
}

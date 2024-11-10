package com.management.services;

import com.management.dto.PlayerRequestDTO;
import com.management.models.Player;
import com.management.repositories.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class PlayerService {
    private static final String PLAYER_NOT_FOUND = "Player not found";
    @Autowired
    private PlayerRepository playerRepository;

    public Player createPlayer(PlayerRequestDTO playerDTO) {
        Player newPlayer = new Player(playerDTO.getName());
        return playerRepository.save(newPlayer);
    }

    public Player getPlayer(String playerId) {
        Optional<Player> updatedPlayer = playerRepository.findById(playerId);
        Player fetchedPlayer = new Player();
        if (updatedPlayer.isPresent()){
            fetchedPlayer = updatedPlayer.get();
        }
        return fetchedPlayer;
    }

    public Player updatePlayer(String playerId, Player playerDetails) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException(PLAYER_NOT_FOUND));

        player.setName(playerDetails.getName());
        player.setPoints(playerDetails.getPoints());
        player.setFouls(playerDetails.getFouls());

        return playerRepository.save(player);
    }

    public void deletePlayer(String playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException(PLAYER_NOT_FOUND));
        playerRepository.delete(player);
    }

    public void logIppon(String playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException(PLAYER_NOT_FOUND));
        player.logIppon();
        playerRepository.save(player);
    }

    public void logWazari(String playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException(PLAYER_NOT_FOUND));
        player.logWazari();
        playerRepository.save(player);
    }

    public void logYoko(String playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException(PLAYER_NOT_FOUND));
        player.logYoko();
        playerRepository.save(player);
    }

    public void logFoul(String playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException(PLAYER_NOT_FOUND));
        player.logFoul();
        playerRepository.save(player);
    }
}
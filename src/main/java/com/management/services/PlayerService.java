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
        Optional<Player> fetchedPlayer = playerRepository.findById(playerId);
        if (fetchedPlayer.isEmpty()){
            throw new IllegalArgumentException(PLAYER_NOT_FOUND);
        }
        return fetchedPlayer.get();
    }

    public Player updatePlayer(String playerId, Player playerDetails) {
        Player player = getPlayer(playerId);

        player.setName(playerDetails.getName());
        player.setPoints(playerDetails.getPoints());
        player.setFouls(playerDetails.getFouls());

        return playerRepository.save(player);
    }

    public void deletePlayer(String playerId) {
        Player player = getPlayer(playerId);
        playerRepository.delete(player);
    }

    public void logIppon(String playerId) {
        Player player = getPlayer(playerId);
        player.logIppon();
        playerRepository.save(player);
    }

    public void logWazari(String playerId) {
        Player player = getPlayer(playerId);
        player.logWazari();
        playerRepository.save(player);
    }

    public void logYoko(String playerId) {
        Player player = getPlayer(playerId);
        player.logYoko();
        playerRepository.save(player);
    }

    public void logFoul(String playerId) {
        Player player = getPlayer(playerId);
        player.logFoul();
        playerRepository.save(player);
    }
}
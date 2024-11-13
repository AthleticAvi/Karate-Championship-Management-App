package com.management.services;

import com.management.dto.PlayerRequestDTO;
import com.management.exceptions.PlayerNotFoundException;
import com.management.models.Player;
import com.management.repositories.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class PlayerService {

    public static final Logger logger = LoggerFactory.getLogger(PlayerService.class);
    private static final String PLAYER_NOT_FOUND = "Player not found";
    private static final String PLAYER_ID = " Player ID: ";
    @Autowired
    private PlayerRepository playerRepository;

    public Player createPlayer(PlayerRequestDTO playerDTO) {
        Player newPlayer = new Player(playerDTO.getName());
        return playerRepository.save(newPlayer);
    }

    public Player getPlayer(String playerId) {
        Optional<Player> fetchedPlayer = playerRepository.findById(playerId);
        if (fetchedPlayer.isEmpty()){
            logger.error("PlayerService - getPlayer - {}  {}: {}", PLAYER_NOT_FOUND, PLAYER_ID, playerId);
            throw new PlayerNotFoundException(PLAYER_NOT_FOUND + PLAYER_ID + playerId);
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
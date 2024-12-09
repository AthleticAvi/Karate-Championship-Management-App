package com.management.services;

import com.management.dto.PlayerRequestDTO;
import com.management.enums.PointsType;
import com.management.exceptions.PlayerNotFoundException;
import com.management.models.Player;
import com.management.repositories.PlayerRepository;
import com.management.util.KumiteGameManagementUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class PlayerService {
    public static final Logger logger = LoggerFactory.getLogger(PlayerService.class);
    private static final String PLAYER_NOT_FOUND = "Player not found";
    private static final String PLAYER_ID = " Player ID: ";
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    @Lazy
    private GameHelperService gameHelperService;

    public Player createPlayer(PlayerRequestDTO playerDTO) {
        logger.debug("PlayerService - createPlayer - Method Started");
        Player newPlayer = new Player(playerDTO.getName());
        logger.debug("PlayerService - createPlayer - Method Ended");
        return playerRepository.save(newPlayer);
    }

    public Player getPlayer(String playerId) {
        logger.debug("PlayerService - getPlayer - Method Started");
        Optional<Player> fetchedPlayer = playerRepository.findById(playerId);
        if (fetchedPlayer.isEmpty()){
            logger.error("PlayerService - getPlayer - {}  {}: {}", PLAYER_NOT_FOUND, PLAYER_ID, playerId);
            throw new PlayerNotFoundException(PLAYER_NOT_FOUND + PLAYER_ID + playerId);
        }
        logger.debug("PlayerService - getPlayer - Method Ended");
        return fetchedPlayer.get();
    }

    public Player updatePlayer(String playerId, Player playerDetails) {
        logger.debug("PlayerService - updatePlayer - Method Started");

        Player player = getPlayer(playerId);
        player.setName(playerDetails.getName());
        player.setPoints(playerDetails.getPoints());
        player.setFouls(playerDetails.getFouls());

        logger.debug("PlayerService - updatePlayer - Method Ended");
        return playerRepository.save(player);
    }

    public void deletePlayer(String playerId) {
        logger.debug("PlayerService - deletePlayer - Method Started");
        Player player = getPlayer(playerId);
        playerRepository.delete(player);
        logger.debug("PlayerService - deletePlayer - Method Ended");
    }

    public void addPoint(String gameId, String color, String pointType) {
        logger.debug("PlayerService - addPoint - Method Started");

        String playerId = gameHelperService.getPlayerIdByGameAndColor(gameId, color);
        Player player = getPlayer(playerId);
        PointsType scoredPoint = KumiteGameManagementUtils.mapPointToPointType(pointType);
        player.addPoint(scoredPoint);
        playerRepository.save(player);
        gameHelperService.updateKumiteGame(gameId, color);
        logger.debug("PlayerService - addPoint - Method Ended");
    }

    public void addFoul(String gameId, String color) {
        logger.debug("PlayerService - addFoul - Method Started");

        String playerId = gameHelperService.getPlayerIdByGameAndColor(gameId, color);
        Player player = getPlayer(playerId);
        player.addFoul();
        playerRepository.save(player);
        gameHelperService.updateKumiteGame(gameId, color);

        logger.debug("PlayerService - addFoul - Method Ended");
    }
}
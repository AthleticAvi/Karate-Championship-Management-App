package com.management.services;

import com.management.dto.PlayerRequestDTO;
import com.management.enums.PointsType;
import com.management.exceptions.PlayerNotFoundException;
import com.management.models.Player;
import com.management.repositories.PlayerRepository;
import com.management.util.KumiteGameManagementUtils;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class PlayerService {
  private static final Logger log = LoggerFactory.getLogger(PlayerService.class);
  private static final String PLAYER_NOT_FOUND = "Player not found";
  private static final String PLAYER_ID = " Player ID: ";
  @Autowired private PlayerRepository playerRepository;
  @Autowired @Lazy private GameHelperService gameHelperService;

  public Player createPlayer(PlayerRequestDTO playerDTO) {
    Player newPlayer = new Player(playerDTO.getName());
    return playerRepository.save(newPlayer);
  }

  public Player getPlayer(String playerId) {
    Optional<Player> fetchedPlayer = playerRepository.findById(playerId);
    if (fetchedPlayer.isEmpty()) {
      log.error("PlayerService - getPlayer - {}  {}: {}", PLAYER_NOT_FOUND, PLAYER_ID, playerId);
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

  public void addPoint(String gameId, String color, String pointType) {
    String playerId = gameHelperService.getPlayerIdByGameAndColor(gameId, color);
    Player player = getPlayer(playerId);
    PointsType scoredPoint = KumiteGameManagementUtils.mapPointToPointType(pointType);
    player.addPoint(scoredPoint);
    playerRepository.save(player);
    gameHelperService.updateKumiteGame(gameId, color);
  }

  public void removePoint(String gameId, String color, String pointType) {

    String playerId = gameHelperService.getPlayerIdByGameAndColor(gameId, color);
    Player player = getPlayer(playerId);
    PointsType pointToRemove = KumiteGameManagementUtils.mapPointToPointType(pointType);
    player.removePoint(pointToRemove);
    playerRepository.save(player);
    gameHelperService.updateKumiteGame(gameId, color);
  }

  public void addFoul(String gameId, String color) {

    String playerId = gameHelperService.getPlayerIdByGameAndColor(gameId, color);
    Player player = getPlayer(playerId);
    player.addFoul();
    playerRepository.save(player);
    gameHelperService.updateKumiteGame(gameId, color);
  }

  public void removeFoul(String gameId, String color) {

    String playerId = gameHelperService.getPlayerIdByGameAndColor(gameId, color);
    Player player = getPlayer(playerId);
    player.removeFoul();
    playerRepository.save(player);
    gameHelperService.updateKumiteGame(gameId, color);
  }
}

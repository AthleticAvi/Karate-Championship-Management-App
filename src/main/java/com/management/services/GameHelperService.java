package com.management.services;

import com.management.dto.PlayerRequestDTO;
import com.management.enums.PlayerColor;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.util.KumiteGameManagementUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GameHelperService {

  @Autowired private KumiteGameService kumiteGameService;
  @Autowired private PlayerService playerService;

  public String getPlayerIdByGameAndColor(String gameId, String color) {
    KumiteGame kumiteGame = kumiteGameService.getKumiteGame(gameId);
    PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);
    return kumiteGame.getPlayersMap().get(playerColor).getId();
  }

  /**
   * Re-syncs the player snapshot inside the match and hands the saved match back.
   *
   * <p>Returns the match rather than {@code void} so a scoring call can report the new score
   * without a second round trip. The caller already paid for the read and the write.
   */
  public KumiteGame updateKumiteGame(String gameId, String color) {
    return kumiteGameService.updateKumiteGamePlayers(gameId, color);
  }

  public Player getPlayerById(String playerId) {
    return playerService.getPlayer(playerId);
  }

  public Player createNewPlayer(PlayerRequestDTO playerDTO) {
    return playerService.createPlayer(playerDTO);
  }
}

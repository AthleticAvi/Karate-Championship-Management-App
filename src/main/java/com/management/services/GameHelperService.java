package com.management.services;

import com.management.dto.PlayerRequestDTO;
import com.management.enums.PlayerColor;
import com.management.exceptions.PlayerNotFoundException;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.util.KumiteGameManagementUtils;
import org.springframework.stereotype.Service;

@Service
public class GameHelperService {

  private final KumiteGameService kumiteGameService;
  private final PlayerService playerService;

  public GameHelperService(KumiteGameService kumiteGameService, PlayerService playerService) {
    this.kumiteGameService = kumiteGameService;
    this.playerService = playerService;
  }

  /**
   * Finds the fighter of the given colour in the given match.
   *
   * <p>The absent case used to dereference {@code null} and surface as a 500. It is a 404: the
   * colour parsed, so the request was well formed, but this match does not field that colour. The
   * separate failure — a string that is not a colour at all — is rejected as 400 by {@link
   * KumiteGameManagementUtils#mapPlayerColor}.
   */
  public String getPlayerIdByGameAndColor(String gameId, String color) {
    KumiteGame kumiteGame = kumiteGameService.getKumiteGame(gameId);
    PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);
    Player player = kumiteGame.getPlayersMap().get(playerColor);
    if (player == null) {
      throw new PlayerNotFoundException("Match " + gameId + " has no " + playerColor + " fighter.");
    }
    return player.getId();
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

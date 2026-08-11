package com.management.services;

import com.management.dto.PlayerRequestDTO;
import com.management.enums.PlayerColor;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.util.KumiteGameManagementUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GameHelperService {

    private static final Logger logger = LoggerFactory.getLogger(GameHelperService.class);

    @Autowired
    private KumiteGameService kumiteGameService;
    @Autowired
    private PlayerService playerService;
    public String getPlayerIdByGameAndColor(String gameId, String color){
        KumiteGame kumiteGame = kumiteGameService.getKumiteGame(gameId);
        PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);
        return kumiteGame.getPlayersMap().get(playerColor).getId();
    }

    public void updateKumiteGame(String gameId, String color) {
        kumiteGameService.updateKumiteGamePlayers(gameId, color);
    }

    public Player getPlayerById(String playerId) {
        return playerService.getPlayer(playerId);
    }

    public Player createNewPlayer(PlayerRequestDTO playerDTO) {
        return playerService.createPlayer(playerDTO);
    }
}

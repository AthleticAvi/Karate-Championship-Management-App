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
        logger.debug("GameHelperService - getPlayerIdByGameAndColor - Method Started");
        KumiteGame kumiteGame = kumiteGameService.getKumiteGame(gameId);
        PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);
        logger.debug("GameHelperService - getPlayerIdByGameAndColor - Method Ended");
        return kumiteGame.getPlayersMap().get(playerColor).getId();
    }

    public void updateKumiteGame(String gameId, String color) {
        logger.debug("GameHelperService - updateKumiteGame - Method Started");
        kumiteGameService.updateKumiteGamePlayers(gameId, color);
        logger.debug("GameHelperService - updateKumiteGame - Method Ended");
    }

    public Player getPlayerById(String playerId) {
        logger.debug("GameHelperService - getPlayerById - Method Reached");
        return playerService.getPlayer(playerId);
    }

    public Player createNewPlayer(PlayerRequestDTO playerDTO) {
        logger.debug("GameHelperService - createNewPlayer - Method Reached");
        return playerService.createPlayer(playerDTO);
    }
}

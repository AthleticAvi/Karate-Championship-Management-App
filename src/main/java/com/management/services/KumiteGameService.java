package com.management.services;

import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.PlayerRequestDTO;
import com.management.enums.PlayerColor;
import com.management.exceptions.GameNotFoundException;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import com.management.util.KumiteGameManagementUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class KumiteGameService {

    private static final Logger logger = LoggerFactory.getLogger(KumiteGameService.class);
    private static final String GAME_NOT_FOUND = "Game not found!";
    private static final String GAME_ID = " Game Id: ";
    @Autowired
    private KumiteGameRepository kumiteGameRepository;
    @Autowired
    private PlayerService playerService;

    public KumiteGame createKumiteGame(KumiteGameRequestDTO gameRequestDTO){
        logger.debug("KumiteGameService - createKumiteGame - Method Started");

        Map<PlayerColor, Player> playersMap = new EnumMap<>(PlayerColor.class);

        gameRequestDTO.getPlayersMap().forEach((key, playerDTO) -> {
            PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(playerDTO.getColor());
            PlayerRequestDTO playerRequestDTO = new PlayerRequestDTO();
            playerRequestDTO.setName(playerDTO.getName());
            Player player = playerService.createPlayer(playerRequestDTO);
            playersMap.put(playerColor, player);
        });

        List<Referee> refereesList = gameRequestDTO.getRefereeList().stream().map(Referee::new).toList();

        KumiteGame kumiteGame = new KumiteGame(playersMap, refereesList);

        logger.debug("KumiteGameService - createKumiteGame - Method ended");
        return kumiteGameRepository.save(kumiteGame);
    }

    public KumiteGame getKumiteGame(String gameId){
        logger.debug("KumiteGameService - getKumiteGame - Method Started");

        Optional<KumiteGame> fetchedKumiteGame = kumiteGameRepository.findById(gameId);
        if (fetchedKumiteGame.isEmpty()){
            logger.error("KumiteGameService - getKumiteGame - couldn't find game with id: {}", gameId);
            throw new GameNotFoundException(GAME_NOT_FOUND + GAME_ID + gameId);
        }

        logger.debug("KumiteGameService - getKumiteGame - Method Ended");
        return fetchedKumiteGame.get();
    }

    public KumiteGame updateKumiteGamePlayers(String gameId, String color){
        logger.debug("KumiteGameService - updateKumiteGamePlayers - Method Started");
        KumiteGame kumiteGame = getKumiteGame(gameId);
        PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);

        String playerId = kumiteGame.getPlayersMap().get(playerColor).getId();
        Player updatedPlayer = playerService.getPlayer(playerId);

        kumiteGame.updatePlayer(playerColor, updatedPlayer);
        logger.debug("KumiteGameService - updateKumiteGamePlayers - Method Ended");
        return kumiteGameRepository.save(kumiteGame);
    }

    public KumiteGame updateKumiteGameWinner(String gameId, String color){
        logger.debug("KumiteGameService - updateKumiteGameWinner - Method Started");

        KumiteGame kumiteGame = getKumiteGame(gameId);
        PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);
        kumiteGame.updateWinner(playerColor);

        logger.debug("KumiteGameService - updateKumiteGameWinner - Method Ended");

        return kumiteGameRepository.save(kumiteGame);
    }
}
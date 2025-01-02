package com.management.services;

import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.PlayerRequestDTO;
import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import com.management.exceptions.GameNotFoundException;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import com.management.util.GameConfig;
import com.management.util.KumiteGameManagementUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
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

    private final GameConfig config = new GameConfig();
    @Autowired
    @Lazy
    private GameHelperService gameHelperService;

    public KumiteGame createKumiteGame(KumiteGameRequestDTO gameRequestDTO) {
        logger.debug("KumiteGameService - createKumiteGame - Method Started");

        validateGameRequestDTO(gameRequestDTO);
        Map<PlayerColor, Player> playersMap = new EnumMap<>(PlayerColor.class);

        gameRequestDTO.getPlayersMap().forEach((key, playerDTO) -> {
            PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(playerDTO.getColor());
            PlayerRequestDTO playerRequestDTO = new PlayerRequestDTO();
            playerRequestDTO.setName(playerDTO.getName());
            Player player = gameHelperService.createNewPlayer(playerRequestDTO);
            playersMap.put(playerColor, player);
        });

        List<Referee> refereesList = gameRequestDTO.getRefereeList().stream().map(Referee::new).toList();

        Duration gameDuration = determineGameDuration(gameRequestDTO);

        KumiteGame kumiteGame = new KumiteGame(playersMap, refereesList, gameDuration);

        logger.debug("KumiteGameService - createKumiteGame - Method ended");
        return saveGame(kumiteGame);
    }

    public KumiteGame startGame(String gameId) {
        logger.debug("KumiteGameService - startGame - Method Started");
        KumiteGame kumiteGame = getKumiteGame(gameId);
        kumiteGame.initializeTimer(kumiteGame.getRemainingTime());
        kumiteGame.setGameState(GameState.RUNNING);
        kumiteGame.setStartTime(LocalDateTime.now());
        kumiteGame.getTimer().start();
        updateRemainingTime(kumiteGame);
        logger.debug("KumiteGameService - startGame - Method Ended");
        return saveGame(kumiteGame);
    }

    public KumiteGame pauseGame(String gameId) {
        logger.debug("KumiteGameService - pauseGame - Method Started");
        KumiteGame kumiteGame = getKumiteGame(gameId);
        kumiteGame.getTimer().pause();
        updateRemainingTime(kumiteGame);
        kumiteGame.setStartTime(null);
        kumiteGame.setGameState(GameState.PAUSED);
        logger.debug("KumiteGameService - pauseGame - Method Ended");
        return saveGame(kumiteGame);
    }

    public KumiteGame resumeGame(String gameId) {
        logger.debug("KumiteGameService - resumeGame - Method Started");
        KumiteGame kumiteGame = getKumiteGame(gameId);
        kumiteGame.initializeTimer(kumiteGame.getRemainingTime());
        kumiteGame.setGameState(GameState.RUNNING);
        kumiteGame.setStartTime(LocalDateTime.now());
        kumiteGame.getTimer().resume();
        logger.debug("KumiteGameService - resumeGame - Method Ended");
        return saveGame(kumiteGame);
    }

    public KumiteGame endGame(String gameId) {
        logger.debug("KumiteGameService - endGame - Method Started");
        KumiteGame kumiteGame = getKumiteGame(gameId);
        kumiteGame.getTimer().stop();
        updateRemainingTime(kumiteGame);
        kumiteGame.setGameState(GameState.FINISHED);
        logger.debug("KumiteGameService - endGame - Method Ended");
        return saveGame(kumiteGame);
    }

    private void updateRemainingTime(KumiteGame kumiteGame) {
        kumiteGame.setRemainingTime(kumiteGame.getTimer().getRemainingTime());
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
        Player updatedPlayer = gameHelperService.getPlayerById(playerId);

        kumiteGame.updatePlayer(playerColor, updatedPlayer);
        logger.debug("KumiteGameService - updateKumiteGamePlayers - Method Ended");
        return saveGame(kumiteGame);
    }

    public KumiteGame updateKumiteGameWinner(String gameId, String color){
        logger.debug("KumiteGameService - updateKumiteGameWinner - Method Started");

        KumiteGame kumiteGame = getKumiteGame(gameId);
        PlayerColor playerColor = KumiteGameManagementUtils.mapPlayerColor(color);
        kumiteGame.updateWinner(playerColor);

        logger.debug("KumiteGameService - updateKumiteGameWinner - Method Ended");

        return saveGame(kumiteGame);
    }

    private void validateGameRequestDTO(KumiteGameRequestDTO gameRequestDTO) {
        if (gameRequestDTO.getPlayersMap() == null || gameRequestDTO.getPlayersMap().isEmpty()) {
            throw new IllegalArgumentException("Players cannot be empty");
        }
        if (gameRequestDTO.getRefereeList() == null || gameRequestDTO.getRefereeList().isEmpty()) {
            throw new IllegalArgumentException("Referee list cannot be empty");
        }
    }

    private Duration determineGameDuration(KumiteGameRequestDTO gameRequestDTO) {
        if (gameRequestDTO.getGameDuration() != null) {
            Duration requestedDuration = Duration.ofSeconds(Integer.parseInt(gameRequestDTO.getGameDuration()));
            if (config.getOptionalDurations().contains(requestedDuration)) {
                return requestedDuration;
            }
        }
        return config.getDefaultDuration();
    }

    private KumiteGame saveGame(KumiteGame kumiteGame) {
        logger.debug("Saving game with ID: {}", kumiteGame.getId());
        return kumiteGameRepository.save(kumiteGame);
    }
}
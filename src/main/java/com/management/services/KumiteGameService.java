package com.management.services;

import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.PlayerRequestDTO;
import com.management.enums.PlayerColor;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.repositories.KumiteGameRepository;
import com.management.repositories.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class KumiteGameService {

    private static final String GAME_NOT_FOUND = "Game not found!";
    @Autowired
    private KumiteGameRepository kumiteGameRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private PlayerService playerService;

    public KumiteGame createKumiteGame(KumiteGameRequestDTO gameRequestDTO){
        Map<PlayerColor, Player> playersMap = new EnumMap<>(PlayerColor.class);

        gameRequestDTO.getPlayersMap().forEach((key, playerDTO) -> {
            PlayerColor playerColor = PlayerColor.valueOf(playerDTO.getColor().toUpperCase());
            PlayerRequestDTO playerRequestDTO = new PlayerRequestDTO();
            playerRequestDTO.setName(playerDTO.getName());
            Player player = playerService.createPlayer(playerRequestDTO);
            playersMap.put(playerColor, player);
        });

        List<Referee> refereesList = gameRequestDTO.getRefereeList().stream().map(Referee::new).toList();

        KumiteGame kumiteGame = new KumiteGame(playersMap, refereesList);
        return kumiteGameRepository.save(kumiteGame);
    }

    //TODO handle the exception upon not finding the game
    public KumiteGame getKumiteGame(String gameId){
        Optional<KumiteGame> fetchedKumiteGame = kumiteGameRepository.findById(gameId);
//        if (fetchedKumiteGame.isEmpty()){
//            //return fetchedKumiteGame.get();
//        }
        return new KumiteGame(fetchedKumiteGame.get().getPlayersMap(), fetchedKumiteGame.orElseThrow().getReferees());
    }

    public KumiteGame updateKumiteGamePlayers(String gameId, PlayerColor color){
        KumiteGame kumiteGame = getKumiteGame(gameId);
        String playerId = kumiteGame.getPlayersMap().get(color).getId();
        Player updatedPlayer = playerService.getPlayer(playerId);
        kumiteGame.updatePlayer(color, updatedPlayer);
        return kumiteGameRepository.save(kumiteGame);
    }
    //TODO provide logic for updating the winner
//    public KumiteGame updateKumiteGameWinner(String gameId){
//        KumiteGame kumiteGame = getKumiteGame(gameId);
//    }
}
package com.management.controllers;

import com.management.dto.PlayerRequestDTO;
import com.management.dto.PlayerResponseDTO;
import com.management.models.Player;
import com.management.services.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    @Autowired
    private PlayerService playerService;

    @PostMapping
    public PlayerResponseDTO createPlayer(@RequestBody PlayerRequestDTO newPlayer){
        Player savedPlayer = playerService.createPlayer(newPlayer);
        return new PlayerResponseDTO (
                savedPlayer.getId(),
                savedPlayer.getName(),
                savedPlayer.getPoints().getPointsCounter(),
                savedPlayer.getFouls().getFoulCounter()
        );
    }

    @GetMapping("/{playerId}")
    public Player getPlayer(@PathVariable String playerId){
        return playerService.getPlayer(playerId);
    }

    @DeleteMapping("/{playerId}")
    public void deletePlayer(@PathVariable String playerId){
        playerService.deletePlayer(playerId);
    }

    @PostMapping("/{playerId}/foul")
    public void logFoul(@PathVariable String playerId) {
        playerService.logFoul(playerId);
    }

}
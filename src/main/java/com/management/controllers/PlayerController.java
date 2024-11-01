package com.management.controllers;

import com.management.dto.PlayerRequestDTO;
import com.management.dto.PlayerResponseDTO;
import com.management.models.Player;
import com.management.services.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

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
    public Optional<Player> getPlayer(@PathVariable String playerId){
        return playerService.getPlayer(playerId);
    }

    @DeleteMapping("/{playerId}")
    public void deletePlayer(@PathVariable String playerId){
        playerService.deletePlayer(playerId);
    }

    @PostMapping("/{playerId}/ippon")
    public void logIppon(@PathVariable String playerId){
        playerService.logIppon(playerId);
    }

    @PostMapping("/{playerId}/wazari")
    public void logWazari(@PathVariable String playerId) {
        playerService.logWazari(playerId);
    }

    @PostMapping("/{playerId}/yoko")
    public void logYoko(@PathVariable String playerId) {
        playerService.logYoko(playerId);
    }

    @PostMapping("/{playerId}/foul")
    public void logFoul(@PathVariable String playerId) {
        playerService.logFoul(playerId);
    }

}
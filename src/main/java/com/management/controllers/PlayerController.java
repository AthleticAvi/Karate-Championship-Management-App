package com.management.controllers;

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
    public Player createPlayer(@RequestBody Player player){
        return playerService.createPlayer(player);
    }

    @GetMapping("/{playerId}")
    public Optional<Player> getPlayer(@PathVariable String playerId){
        return playerService.getPlayer(playerId);
    }

    @PutMapping("/{playerId}")
    public Player updatePlayer(@PathVariable String playerId, @RequestBody Player playerDetails){
        return playerService.updatePlayer(playerId, playerDetails);
    }

    @DeleteMapping("{playerId}")
    public void deletePlayer(@PathVariable String playerId){
        playerService.deletePlayer(playerId);
    }

    @PostMapping("{playerId}/ippon")
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

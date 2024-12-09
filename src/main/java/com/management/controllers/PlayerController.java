package com.management.controllers;

import com.management.dto.PlayerRequestDTO;
import com.management.dto.PlayerResponseDTO;
import com.management.models.Player;
import com.management.services.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    @Autowired
    private PlayerService playerService;

    @PostMapping
    public ResponseEntity<PlayerResponseDTO> createPlayer(@RequestBody PlayerRequestDTO newPlayer) {
        Player savedPlayer = playerService.createPlayer(newPlayer);
        PlayerResponseDTO response = new PlayerResponseDTO (
                savedPlayer.getId(),
                savedPlayer.getName(),
                savedPlayer.getPoints().getNumOfPoints(),
                savedPlayer.getFouls().getNumOfFouls()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/players/" + savedPlayer.getId())
                .body(response);
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<Player> getPlayer(@PathVariable String playerId) {
        Player player = playerService.getPlayer(playerId);
        return ResponseEntity.ok(player);
    }

    @DeleteMapping("/{playerId}")
    public ResponseEntity<String> deletePlayer(@PathVariable String playerId) {
        playerService.deletePlayer(playerId);
        return ResponseEntity.ok("Player deleted successfully.");
    }

}
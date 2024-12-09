package com.management.controllers;

import com.management.dto.KumiteGameRequestDTO;
import com.management.models.KumiteGame;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kumitegame")
public class KumiteGameController {

    @Autowired
    private KumiteGameService kumiteGameService;

    @Autowired
    private PlayerService playerService;

    @PostMapping
    public ResponseEntity<KumiteGame> createKumiteGame(@RequestBody KumiteGameRequestDTO gameDetails) {
        KumiteGame createdGame = kumiteGameService.createKumiteGame(gameDetails);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/kumitegame/" + createdGame.getId())
                .body(createdGame);
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<KumiteGame> getKumiteGame(@PathVariable String gameId) {
        KumiteGame kumiteGame = kumiteGameService.getKumiteGame(gameId);
        return ResponseEntity.ok(kumiteGame);
    }

    @PutMapping("/{gameId}/add-point")
    public ResponseEntity<String> addPoint(
            @PathVariable String gameId,
            @RequestParam String color,
            @RequestParam String pointType) {
            playerService.addPoint(gameId, color, pointType);
            return ResponseEntity.ok("Point added successfully.");
    }

    @PutMapping("/{gameId}/remove-point")
    public ResponseEntity<String> removePoint(
            @PathVariable String gameId,
            @RequestParam String color,
            @RequestParam String pointType) {
        playerService.removePoint(gameId, color, pointType);
        return ResponseEntity.ok("Point removed successfully.");
    }

    @PutMapping("/{gameId}/add-foul")
    public ResponseEntity<String> addFoul(
            @PathVariable String gameId,
            @RequestParam String color) {
        playerService.addFoul(gameId, color);
        return ResponseEntity.ok("Foul added successfully.");
    }

    @PutMapping("/{gameId}/remove-foul")
    public ResponseEntity<String> removeFoul(
            @PathVariable String gameId,
            @RequestParam String color) {
        playerService.removeFoul(gameId, color);
        return ResponseEntity.ok("Foul removed successfully.");
    }

    @PutMapping("/{gameId}/update-player/{color}")
    public ResponseEntity<KumiteGame> updatePlayerInKumiteGame(
            @PathVariable String gameId,
            @PathVariable String color) {
        KumiteGame updatedGame = kumiteGameService.updateKumiteGamePlayers(gameId, color);
        return ResponseEntity.ok(updatedGame);
    }

    @PutMapping("/{gameId}/update-winner/{color}")
    public ResponseEntity<KumiteGame> updateKumiteGameWinner(
            @PathVariable String gameId,
            @PathVariable String color) {
        KumiteGame updatedGame = kumiteGameService.updateKumiteGameWinner(gameId, color);
        return ResponseEntity.ok(updatedGame);
    }
}
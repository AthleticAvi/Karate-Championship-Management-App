package com.management.controllers;

import com.management.dto.KumiteGameRequestDTO;
import com.management.models.KumiteGame;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public KumiteGame createKumiteGame(@RequestBody KumiteGameRequestDTO gameDetails){
        return kumiteGameService.createKumiteGame(gameDetails);
    }

    @GetMapping("/{gameId}")
    public KumiteGame getKumiteGame(@PathVariable String gameId){
        return kumiteGameService.getKumiteGame(gameId);
    }

    @PutMapping("/{gameId}/add-point")
    public ResponseEntity<String> addPoint(
            @PathVariable String gameId,
            @RequestParam String color,
            @RequestParam String pointType) {
            playerService.addPoint(gameId, color, pointType);
            return ResponseEntity.ok("Points updated successfully.");
    }

    @PutMapping("/{gameId}/remove-point")
    public ResponseEntity<String> removePoint(
            @PathVariable String gameId,
            @RequestParam String color,
            @RequestParam String pointType) {
        playerService.removePoint(gameId, color, pointType);
        return ResponseEntity.ok("Points updated successfully.");
    }

    @PutMapping("/{gameId}/add-foul")
    public ResponseEntity<String> addFoul(
            @PathVariable String gameId,
            @RequestParam String color) {
        playerService.addFoul(gameId, color);
        return ResponseEntity.ok("Foul updated successfully.");
    }

    @PutMapping("/{gameId}/remove-foul")
    public ResponseEntity<String> removeFoul(
            @PathVariable String gameId,
            @RequestParam String color) {
        playerService.removeFoul(gameId, color);
        return ResponseEntity.ok("Foul updated successfully.");
    }

    @PutMapping("/{gameId}/update-player/{color}")
    public KumiteGame updatePlayerInKumiteGame(
            @PathVariable String gameId,
            @PathVariable String color) {
        return kumiteGameService.updateKumiteGamePlayers(gameId, color);
    }

    @PutMapping("/{gameId}/update-winner/{color}")
    public KumiteGame updateKumiteGameWinner(
            @PathVariable String gameId,
            @PathVariable String color) {
        return kumiteGameService.updateKumiteGameWinner(gameId, color);
    }
}
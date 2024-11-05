package com.management.controllers;

import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.PlayerDTO;
import com.management.enums.PlayerColor;
import com.management.models.KumiteGame;
import com.management.services.KumiteGameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kumiteGames")
public class KumiteGameController {

    @Autowired
    private KumiteGameService kumiteGameService;

    @PostMapping
    public KumiteGame createKumiteGame(@RequestBody KumiteGameRequestDTO gameDetails){
        return kumiteGameService.createKumiteGame(gameDetails);
    }

    @GetMapping("/{gameId}")
    public KumiteGame getKumiteGame(@PathVariable String gameId){
        return kumiteGameService.getKumiteGame(gameId);
    }

    @PutMapping("/{gameId}/updatePlayer/{color}")
    public KumiteGame updatePlayerInKumiteGame(
            @PathVariable String gameId,
            @PathVariable PlayerColor color,
            @RequestBody PlayerDTO updatedPlayerId) {
        return kumiteGameService.updateKumiteGame(gameId, color, updatedPlayerId);
    }
}
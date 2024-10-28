package com.management.controllers;

import com.management.models.KumiteGame;
import com.management.services.KumiteGameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequestMapping("/api/kumiteGames")
public class KumiteGameController {

    @Autowired
    private KumiteGameService kumiteGameService;

    //TODO posting a game isn't saving the players correctly
    @PostMapping
    public KumiteGame createKumiteGame(@RequestBody KumiteGame gameDetails){
        return kumiteGameService.createKumiteGame(gameDetails.getPlayers(), gameDetails.getReferees(), gameDetails.getDuration());
    }

    @GetMapping("/{gameId}")
    public Optional<KumiteGame> getKumiteGame(@PathVariable String gameId){
        return kumiteGameService.getKumiteGame(gameId);
    }

    //TODO Fix the endpoint (not working through postman)
    @PostMapping("/{gameId}/addTime")
    public void addTime(@PathVariable String gameId, @RequestParam int seconds){
        kumiteGameService.addTime(gameId, Duration.ofSeconds(seconds));
    }

    @PostMapping("/{gameId}/end")
    public void endGame(@PathVariable String gameId){
        kumiteGameService.endKumiteGame(gameId);
    }
}

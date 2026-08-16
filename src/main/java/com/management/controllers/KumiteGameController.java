package com.management.controllers;

import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.KumiteGameResponse;
import com.management.mappers.KumiteGameMapper;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kumitegame")
public class KumiteGameController {

  private final KumiteGameService kumiteGameService;
  private final PlayerService playerService;

  public KumiteGameController(KumiteGameService kumiteGameService, PlayerService playerService) {
    this.kumiteGameService = kumiteGameService;
    this.playerService = playerService;
  }

  @PostMapping
  public ResponseEntity<KumiteGameResponse> createKumiteGame(
      @Valid @RequestBody KumiteGameRequestDTO gameDetails) {
    KumiteGameResponse created =
        KumiteGameMapper.toResponse(kumiteGameService.createKumiteGame(gameDetails));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Location", "/api/kumitegame/" + created.id())
        .body(created);
  }

  @GetMapping("/{gameId}")
  public ResponseEntity<KumiteGameResponse> getKumiteGame(@PathVariable String gameId) {
    return ResponseEntity.ok(KumiteGameMapper.toResponse(kumiteGameService.getKumiteGame(gameId)));
  }

  @PutMapping("/{gameId}/add-point")
  public ResponseEntity<KumiteGameResponse> addPoint(
      @PathVariable String gameId, @RequestParam String color, @RequestParam String pointType) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(playerService.addPoint(gameId, color, pointType)));
  }

  @PutMapping("/{gameId}/remove-point")
  public ResponseEntity<KumiteGameResponse> removePoint(
      @PathVariable String gameId, @RequestParam String color, @RequestParam String pointType) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(playerService.removePoint(gameId, color, pointType)));
  }

  @PutMapping("/{gameId}/add-foul")
  public ResponseEntity<KumiteGameResponse> addFoul(
      @PathVariable String gameId, @RequestParam String color) {
    return ResponseEntity.ok(KumiteGameMapper.toResponse(playerService.addFoul(gameId, color)));
  }

  @PutMapping("/{gameId}/remove-foul")
  public ResponseEntity<KumiteGameResponse> removeFoul(
      @PathVariable String gameId, @RequestParam String color) {
    return ResponseEntity.ok(KumiteGameMapper.toResponse(playerService.removeFoul(gameId, color)));
  }

  @PutMapping("/{gameId}/update-winner/{color}")
  public ResponseEntity<KumiteGameResponse> updateKumiteGameWinner(
      @PathVariable String gameId, @PathVariable String color) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.updateKumiteGameWinner(gameId, color)));
  }
}

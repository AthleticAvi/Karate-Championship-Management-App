package com.management.controllers;

import com.management.dto.PlayerRequestDTO;
import com.management.dto.PlayerResponse;
import com.management.mappers.PlayerMapper;
import com.management.services.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

  @Autowired private PlayerService playerService;

  @PostMapping
  public ResponseEntity<PlayerResponse> createPlayer(@RequestBody PlayerRequestDTO newPlayer) {
    PlayerResponse created = PlayerMapper.toResponse(playerService.createPlayer(newPlayer));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Location", "/api/players/" + created.id())
        .body(created);
  }

  @GetMapping("/{playerId}")
  public ResponseEntity<PlayerResponse> getPlayer(@PathVariable String playerId) {
    return ResponseEntity.ok(PlayerMapper.toResponse(playerService.getPlayer(playerId)));
  }

  @DeleteMapping("/{playerId}")
  public ResponseEntity<String> deletePlayer(@PathVariable String playerId) {
    playerService.deletePlayer(playerId);
    return ResponseEntity.ok("Player deleted successfully.");
  }
}

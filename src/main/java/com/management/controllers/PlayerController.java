package com.management.controllers;

import com.management.dto.PlayerRequestDTO;
import com.management.dto.PlayerResponse;
import com.management.mappers.PlayerMapper;
import com.management.services.PlayerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

  @Autowired private PlayerService playerService;

  @PostMapping
  public ResponseEntity<PlayerResponse> createPlayer(
      @Valid @RequestBody PlayerRequestDTO newPlayer) {
    PlayerResponse created = PlayerMapper.toResponse(playerService.createPlayer(newPlayer));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Location", "/api/players/" + created.id())
        .body(created);
  }

  @GetMapping("/{playerId}")
  public ResponseEntity<PlayerResponse> getPlayer(@PathVariable String playerId) {
    return ResponseEntity.ok(PlayerMapper.toResponse(playerService.getPlayer(playerId)));
  }

  /**
   * Deletes a fighter.
   *
   * <p>204 rather than 200 with the sentence "Player deleted successfully." A success sentence is
   * not a contract — it cannot be parsed, it says nothing the status code does not, and it commits
   * the API to an English string. The status table in {@code workflow/patterns/service-exposure.md}
   * gives 204 for "succeeded, nothing to return".
   */
  @DeleteMapping("/{playerId}")
  public ResponseEntity<Void> deletePlayer(@PathVariable String playerId) {
    playerService.deletePlayer(playerId);
    return ResponseEntity.noContent().build();
  }
}

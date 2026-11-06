package com.management.controllers;

import com.management.dto.PlayerRequestDTO;
import com.management.dto.PlayerResponse;
import com.management.mappers.PlayerMapper;
import com.management.services.PlayerService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PlayerController.PATH)
public class PlayerController {

  static final String PATH = "/players";

  private final PlayerService playerService;

  public PlayerController(PlayerService playerService) {
    this.playerService = playerService;
  }

  @ApiResponse(responseCode = "201", description = "Created")
  @PostMapping
  public ResponseEntity<PlayerResponse> createPlayer(
      @Valid @RequestBody PlayerRequestDTO newPlayer) {
    PlayerResponse created = PlayerMapper.toResponse(playerService.createPlayer(newPlayer));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Location", ApiVersionConfiguration.PREFIX + PATH + "/" + created.id())
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
   * the API to an English string. The status table in {@code
   * context/workflow/design/service-exposure.md} gives 204 for "succeeded, nothing to return".
   */
  @ApiResponse(responseCode = "204", description = "No Content")
  @DeleteMapping("/{playerId}")
  public ResponseEntity<Void> deletePlayer(@PathVariable String playerId) {
    playerService.deletePlayer(playerId);
    return ResponseEntity.noContent().build();
  }
}

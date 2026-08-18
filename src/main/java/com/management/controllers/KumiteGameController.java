package com.management.controllers;

import com.management.dto.ClockAdjustmentRequest;
import com.management.dto.KumiteGameRequestDTO;
import com.management.dto.KumiteGameResponse;
import com.management.dto.WinnerOverrideRequest;
import com.management.mappers.KumiteGameMapper;
import com.management.services.KumiteGameService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kumitegame")
public class KumiteGameController {

  private final KumiteGameService kumiteGameService;

  public KumiteGameController(KumiteGameService kumiteGameService) {
    this.kumiteGameService = kumiteGameService;
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
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.getGameWithFighters(gameId)));
  }

  @PutMapping("/{gameId}/add-point")
  public ResponseEntity<KumiteGameResponse> addPoint(
      @PathVariable String gameId, @RequestParam String color, @RequestParam String pointType) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.addPoint(gameId, color, pointType)));
  }

  @PutMapping("/{gameId}/remove-point")
  public ResponseEntity<KumiteGameResponse> removePoint(
      @PathVariable String gameId, @RequestParam String color, @RequestParam String pointType) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.removePoint(gameId, color, pointType)));
  }

  @PutMapping("/{gameId}/add-foul")
  public ResponseEntity<KumiteGameResponse> addFoul(
      @PathVariable String gameId, @RequestParam String color) {
    return ResponseEntity.ok(KumiteGameMapper.toResponse(kumiteGameService.addFoul(gameId, color)));
  }

  @PutMapping("/{gameId}/remove-foul")
  public ResponseEntity<KumiteGameResponse> removeFoul(
      @PathVariable String gameId, @RequestParam String color) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.removeFoul(gameId, color)));
  }

  @PutMapping("/{gameId}/update-winner/{color}")
  public ResponseEntity<KumiteGameResponse> updateKumiteGameWinner(
      @PathVariable String gameId, @PathVariable String color) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.updateKumiteGameWinner(gameId, color)));
  }

  /** Puts a fighter out of the match; the rules engine awards the win to the opponent. */
  @PutMapping("/{gameId}/disqualify/{color}")
  public ResponseEntity<KumiteGameResponse> disqualify(
      @PathVariable String gameId, @PathVariable String color) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.disqualify(gameId, color)));
  }

  /** Records a forfeit — KIKEN — against a fighter who did not appear or withdrew. */
  @PutMapping("/{gameId}/kiken/{color}")
  public ResponseEntity<KumiteGameResponse> forfeit(
      @PathVariable String gameId, @PathVariable String color) {
    return ResponseEntity.ok(KumiteGameMapper.toResponse(kumiteGameService.forfeit(gameId, color)));
  }

  /** Replaces the result with a referee's decision. The reason is required. */
  @PutMapping("/{gameId}/override-winner")
  public ResponseEntity<KumiteGameResponse> overrideWinner(
      @PathVariable String gameId, @Valid @RequestBody WinnerOverrideRequest request) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.overrideWinner(gameId, request)));
  }

  /** Puts time back on the clock after a stop that came late. */
  @PutMapping("/{gameId}/add-time")
  public ResponseEntity<KumiteGameResponse> addTime(
      @PathVariable String gameId, @Valid @RequestBody ClockAdjustmentRequest request) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.addTime(gameId, request)));
  }
}

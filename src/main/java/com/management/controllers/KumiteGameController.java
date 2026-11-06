package com.management.controllers;

import com.management.dto.ClockAdjustmentRequest;
import com.management.dto.KumiteGameRequest;
import com.management.dto.KumiteGameResponse;
import com.management.dto.WinnerOverrideRequest;
import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.mappers.KumiteGameMapper;
import com.management.services.KumiteGameService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(KumiteGameController.PATH)
public class KumiteGameController {

  static final String PATH = "/kumitegame";

  private final KumiteGameService kumiteGameService;

  public KumiteGameController(KumiteGameService kumiteGameService) {
    this.kumiteGameService = kumiteGameService;
  }

  @ApiResponse(responseCode = "201", description = "Created")
  @PostMapping
  public ResponseEntity<KumiteGameResponse> createKumiteGame(
      @Valid @RequestBody KumiteGameRequest gameDetails) {
    KumiteGameResponse created =
        KumiteGameMapper.toResponse(
            kumiteGameService.createKumiteGame(KumiteGameMapper.toNewMatch(gameDetails)));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Location", ApiVersionConfiguration.PREFIX + PATH + "/" + created.id())
        .body(created);
  }

  @GetMapping("/{gameId}")
  public ResponseEntity<KumiteGameResponse> getKumiteGame(@PathVariable String gameId) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.getGameWithFighters(gameId)));
  }

  @PutMapping("/{gameId}/add-point")
  public ResponseEntity<KumiteGameResponse> addPoint(
      @PathVariable String gameId,
      @Parameter(schema = @Schema(implementation = PlayerColor.class)) @RequestParam String color,
      @Parameter(schema = @Schema(implementation = PointsType.class)) @RequestParam
          String pointType) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.addPoint(gameId, color, pointType)));
  }

  @PutMapping("/{gameId}/remove-point")
  public ResponseEntity<KumiteGameResponse> removePoint(
      @PathVariable String gameId,
      @Parameter(schema = @Schema(implementation = PlayerColor.class)) @RequestParam String color,
      @Parameter(schema = @Schema(implementation = PointsType.class)) @RequestParam
          String pointType) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.removePoint(gameId, color, pointType)));
  }

  @PutMapping("/{gameId}/add-foul")
  public ResponseEntity<KumiteGameResponse> addFoul(
      @PathVariable String gameId,
      @Parameter(schema = @Schema(implementation = PlayerColor.class)) @RequestParam String color) {
    return ResponseEntity.ok(KumiteGameMapper.toResponse(kumiteGameService.addFoul(gameId, color)));
  }

  @PutMapping("/{gameId}/remove-foul")
  public ResponseEntity<KumiteGameResponse> removeFoul(
      @PathVariable String gameId,
      @Parameter(schema = @Schema(implementation = PlayerColor.class)) @RequestParam String color) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.removeFoul(gameId, color)));
  }

  @PutMapping("/{gameId}/update-winner/{color}")
  public ResponseEntity<KumiteGameResponse> updateKumiteGameWinner(
      @PathVariable String gameId,
      @Parameter(schema = @Schema(implementation = PlayerColor.class)) @PathVariable String color) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.updateKumiteGameWinner(gameId, color)));
  }

  /** Puts a fighter out of the match; the rules engine awards the win to the opponent. */
  @PutMapping("/{gameId}/disqualify/{color}")
  public ResponseEntity<KumiteGameResponse> disqualify(
      @PathVariable String gameId,
      @Parameter(schema = @Schema(implementation = PlayerColor.class)) @PathVariable String color) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(kumiteGameService.disqualify(gameId, color)));
  }

  /** Records a forfeit — KIKEN — against a fighter who did not appear or withdrew. */
  @PutMapping("/{gameId}/kiken/{color}")
  public ResponseEntity<KumiteGameResponse> forfeit(
      @PathVariable String gameId,
      @Parameter(schema = @Schema(implementation = PlayerColor.class)) @PathVariable String color) {
    return ResponseEntity.ok(KumiteGameMapper.toResponse(kumiteGameService.forfeit(gameId, color)));
  }

  /** Replaces the result with a referee's decision. The reason is required. */
  @PutMapping("/{gameId}/override-winner")
  public ResponseEntity<KumiteGameResponse> overrideWinner(
      @PathVariable String gameId, @Valid @RequestBody WinnerOverrideRequest request) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(
            kumiteGameService.overrideWinner(
                gameId, request.winner(), request.reason(), request.decidedBy())));
  }

  /** Puts time back on the clock after a stop that came late. */
  @PutMapping("/{gameId}/add-time")
  public ResponseEntity<KumiteGameResponse> addTime(
      @PathVariable String gameId, @Valid @RequestBody ClockAdjustmentRequest request) {
    return ResponseEntity.ok(
        KumiteGameMapper.toResponse(
            kumiteGameService.addTime(gameId, request.seconds(), request.addedBy())));
  }
}

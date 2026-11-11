package com.management.kumitegame.web;

import com.management.config.KumiteGameProperties;
import com.management.kumitegame.domain.Recording;
import com.management.kumitegame.service.KumiteGameService;
import com.management.web.ApiVersionConfiguration;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kumite games, and every action recorded on one, each a sub-resource that reads as a thing.
 *
 * <p>Every mutation answers the game as it now stands and the warnings the action raised. Who
 * recorded it comes from the signed-in account, never from a body. An entry carries the id the
 * client gave it: sending the same action again applies it once.
 */
@RestController
@RequestMapping(KumiteGameController.PATH)
public class KumiteGameController {

  public static final String PATH = "/kumite-games";

  private static final String CURRENT_GAME =
      "Conflict. Refused, or another write won: on a lost race `current` holds the game as it now"
          + " stands";

  private final KumiteGameService kumiteGameService;
  private final KumiteGameProperties properties;

  public KumiteGameController(
      KumiteGameService kumiteGameService, KumiteGameProperties properties) {
    this.kumiteGameService = kumiteGameService;
    this.properties = properties;
  }

  @ApiResponse(responseCode = "201", description = "Created")
  @PostMapping
  public ResponseEntity<RecordedResponse> createGame(
      @Valid @RequestBody KumiteGameRequest newGame, Principal principal) {
    RecordedResponse created =
        answer(kumiteGameService.create(KumiteGameMapper.toNewGame(newGame), principal.getName()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Location", ApiVersionConfiguration.PREFIX + PATH + "/" + created.game().id())
        .body(created);
  }

  @GetMapping("/{gameId}")
  public KumiteGameResponse getGame(@PathVariable String gameId) {
    return KumiteGameMapper.toResponse(kumiteGameService.get(gameId), properties.sideColours());
  }

  /**
   * Replaces the settings, only before the start. Absolute, so a lost race is not retried.
   *
   * <p>The 200 is stated because stating any status stops the generator adding its own.
   */
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(
      responseCode = "409",
      description = CURRENT_GAME,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
  @PutMapping("/{gameId}/settings")
  public RecordedResponse changeSettings(
      @PathVariable String gameId, @Valid @RequestBody SettingsRequest settings) {
    return answer(
        kumiteGameService.changeSettings(gameId, KumiteGameMapper.toNewSettings(settings)));
  }

  @PostMapping("/{gameId}/scores")
  public RecordedResponse recordScore(
      @PathVariable String gameId, @Valid @RequestBody ScoreRequest score, Principal principal) {
    return answer(
        kumiteGameService.recordScore(
            gameId, KumiteGameMapper.toNewScore(score), principal.getName()));
  }

  @PostMapping("/{gameId}/penalties")
  public RecordedResponse recordPenalty(
      @PathVariable String gameId,
      @Valid @RequestBody PenaltyRequest penalty,
      Principal principal) {
    return answer(
        kumiteGameService.recordPenalty(
            gameId, KumiteGameMapper.toNewPenalty(penalty), principal.getName()));
  }

  /** SET_TIME is absolute, so a lost race answers 409 with the game as it now stands. */
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(
      responseCode = "409",
      description = CURRENT_GAME,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
  @PostMapping("/{gameId}/clock-actions")
  public RecordedResponse recordClockAction(
      @PathVariable String gameId,
      @Valid @RequestBody ClockActionRequest action,
      Principal principal) {
    return answer(
        kumiteGameService.recordClockAction(
            gameId, KumiteGameMapper.toNewClockAction(action), principal.getName()));
  }

  @PostMapping("/{gameId}/assignments")
  public RecordedResponse recordAssignment(
      @PathVariable String gameId,
      @Valid @RequestBody AssignmentRequest assignment,
      Principal principal) {
    return answer(
        kumiteGameService.recordAssignment(
            gameId, KumiteGameMapper.toNewAssignment(assignment), principal.getName()));
  }

  @PostMapping("/{gameId}/amendments")
  public RecordedResponse recordAmendment(
      @PathVariable String gameId,
      @Valid @RequestBody AmendmentRequest amendment,
      Principal principal) {
    return answer(
        kumiteGameService.recordAmendment(
            gameId, KumiteGameMapper.toNewAmendment(amendment), principal.getName()));
  }

  /** Absolute, so a lost race answers 409 with the game as it now stands. */
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(
      responseCode = "409",
      description = CURRENT_GAME,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
  @PostMapping("/{gameId}/results")
  public RecordedResponse recordResult(
      @PathVariable String gameId, @Valid @RequestBody ResultRequest result, Principal principal) {
    return answer(
        kumiteGameService.recordResult(
            gameId, KumiteGameMapper.toNewResult(result), principal.getName()));
  }

  private RecordedResponse answer(Recording recording) {
    return KumiteGameMapper.toResponse(recording, properties.sideColours());
  }
}

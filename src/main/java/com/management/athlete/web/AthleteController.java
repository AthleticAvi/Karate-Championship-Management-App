package com.management.athlete.web;

import com.management.athlete.service.AthleteService;
import com.management.web.ApiVersionConfiguration;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates, reads, updates and deletes athletes, each kept once, outside any game. */
@RestController
@RequestMapping(AthleteController.PATH)
public class AthleteController {

  public static final String PATH = "/athletes";

  private final AthleteService athleteService;

  public AthleteController(AthleteService athleteService) {
    this.athleteService = athleteService;
  }

  @ApiResponse(responseCode = "201", description = "Created")
  @PostMapping
  public ResponseEntity<AthleteResponse> createAthlete(
      @Valid @RequestBody AthleteRequest newAthlete) {
    AthleteResponse created =
        AthleteMapper.toResponse(athleteService.create(newAthlete.name(), newAthlete.weightKg()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Location", ApiVersionConfiguration.PREFIX + PATH + "/" + created.id())
        .body(created);
  }

  @GetMapping("/{athleteId}")
  public AthleteResponse getAthlete(@PathVariable String athleteId) {
    return AthleteMapper.toResponse(athleteService.get(athleteId));
  }

  /**
   * Answers the updated athlete, or 409 with the athlete as it now stands if another write won.
   *
   * <p>The 200 is stated because stating any status stops the generator adding its own.
   */
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(
      responseCode = "409",
      description = "Conflict. Another write won: `current` holds the athlete as it now stands",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
  @PutMapping("/{athleteId}")
  public AthleteResponse updateAthlete(
      @PathVariable String athleteId, @Valid @RequestBody AthleteRequest update) {
    return AthleteMapper.toResponse(
        athleteService.update(athleteId, update.name(), update.weightKg()));
  }

  /** Refused with 409 while a game refers to the athlete. */
  @ApiResponse(responseCode = "204", description = "No Content")
  @DeleteMapping("/{athleteId}")
  public ResponseEntity<Void> deleteAthlete(@PathVariable String athleteId) {
    athleteService.delete(athleteId);
    return ResponseEntity.noContent().build();
  }
}

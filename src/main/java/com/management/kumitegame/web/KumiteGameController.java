package com.management.kumitegame.web;

import com.fasterxml.jackson.annotation.JsonView;
import com.management.config.KumiteGameProperties;
import com.management.kumitegame.domain.ActingRole;
import com.management.kumitegame.domain.Recording;
import com.management.kumitegame.service.KumiteGameService;
import com.management.kumitegame.service.VersionedGameRead;
import com.management.web.ApiVersionConfiguration;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

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

  /** The scorekeeper's view of one game, under the game's own path, which is the public view. */
  public static final String FULL_VIEW = "/full-view";

  private static final String TAG =
      "Changes when the game, or an athlete or referee it names, changes. Send it back in"
          + " If-None-Match";

  private static final String NOT_MODIFIED =
      "Not Modified. The tag sent in If-None-Match is still current: keep the game already held."
          + " No body";

  private static final String CURRENT_GAME =
      "Conflict. Refused, or another write won: on a lost race `current` holds the game as it now"
          + " stands";

  private final KumiteGameService kumiteGameService;
  private final KumiteGameProperties properties;
  private final long startedAt;

  /**
   * Takes the application's start once, for the tags.
   *
   * @param context the running application, whose start goes into every tag: see {@link #tagOf}
   */
  public KumiteGameController(
      KumiteGameService kumiteGameService,
      KumiteGameProperties properties,
      ApplicationContext context) {
    this.kumiteGameService = kumiteGameService;
    this.properties = properties;
    this.startedAt = context.getStartupDate();
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

  /**
   * The public view: anyone may follow a game. Who recorded each entry and every warning are left
   * out.
   *
   * @return empty on a 304, when the caller's tag is still current
   */
  @JsonView(GameViews.Public.class)
  @ApiResponse(
      responseCode = "200",
      description = "OK, with the game's tag",
      headers = @Header(name = HttpHeaders.ETAG, description = TAG))
  @ApiResponse(
      responseCode = "304",
      description = NOT_MODIFIED,
      content = @Content,
      headers = @Header(name = HttpHeaders.ETAG, description = TAG))
  @GetMapping("/{gameId}")
  public @Nullable KumiteGameResponse getGame(@PathVariable String gameId, WebRequest request) {
    return readTagged(gameId, request);
  }

  /**
   * The scorekeeper's view: everything the public view shows, plus who recorded each entry and
   * every warning.
   *
   * @return empty on a 304, when the caller's tag is still current
   */
  @ApiResponse(
      responseCode = "200",
      description = "OK, with the game's tag",
      headers = @Header(name = HttpHeaders.ETAG, description = TAG))
  @ApiResponse(
      responseCode = "304",
      description = NOT_MODIFIED,
      content = @Content,
      headers = @Header(name = HttpHeaders.ETAG, description = TAG))
  @GetMapping("/{gameId}" + FULL_VIEW)
  public @Nullable KumiteGameResponse getFullView(@PathVariable String gameId, WebRequest request) {
    return readTagged(gameId, request);
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

  @PostMapping("/{gameId}/senshu-records")
  public RecordedResponse recordSenshu(
      @PathVariable String gameId,
      @Valid @RequestBody SenshuRecordRequest record,
      Principal principal) {
    return answer(
        kumiteGameService.recordSenshu(
            gameId, KumiteGameMapper.toNewSenshuRecord(record), principal.getName()));
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

  /**
   * Approves, reopens or voids. Absolute, so a lost race answers 409 with the game as it now
   * stands.
   *
   * <p>The role table admits a Scorekeeper and an Admin, because which a move needs depends on the
   * status: the game refuses an Admin's move from a Scorekeeper with a 403. The role goes down as a
   * plain value, the way the account name does.
   */
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(
      responseCode = "403",
      description =
          "Forbidden. Reopening an APPROVED game and voiding are an Admin's moves, whatever the role"
              + " table admits",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
  @ApiResponse(
      responseCode = "409",
      description = CURRENT_GAME,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
  @PostMapping("/{gameId}/status-changes")
  public RecordedResponse recordStatusChange(
      @PathVariable String gameId,
      @Valid @RequestBody StatusChangeRequest change,
      Principal principal,
      HttpServletRequest request) {
    ActingRole role =
        request.isUserInRole(ActingRole.ADMIN.name()) ? ActingRole.ADMIN : ActingRole.SCOREKEEPER;
    return answer(
        kumiteGameService.recordStatusChange(
            gameId, KumiteGameMapper.toNewStatusChange(change), role, principal.getName()));
  }

  private RecordedResponse answer(Recording recording) {
    return KumiteGameMapper.toResponse(recording, properties.sideColours());
  }

  /**
   * Decides the tag from the versions before the body is built: an unchanged game answers 304 with
   * no body, and the framework sends the tag on both answers.
   */
  private @Nullable KumiteGameResponse readTagged(String gameId, WebRequest request) {
    VersionedGameRead versioned = kumiteGameService.getVersioned(gameId);
    if (request.checkNotModified(tagOf(versioned))) {
      return null;
    }
    return KumiteGameMapper.toResponse(versioned.read(), properties.sideColours());
  }

  /**
   * A weak tag over every version the game's answer shows: the game's, and each athlete's and
   * referee's it names, by id, so a rename changes it and so does one deleted in the accepted race.
   *
   * <p>The application's start goes in too. A redeploy can change what the same stored game reads
   * as, such as the side colours or the suggested result, and a tag kept across it would hold an
   * APPROVED game, which never changes again, at its old answer for good. A restart costs each
   * caller one full answer.
   *
   * <p>Weak, because two answers at the same versions still differ in {@code serverTime} and a
   * running clock's {@code remainingMs}. The digest is the one the framework's own body tags use:
   * it shortens the tag and guards nothing.
   */
  private String tagOf(VersionedGameRead versioned) {
    String versions =
        startedAt
            + "|"
            + versioned.gameVersion()
            + "|"
            + new TreeMap<>(versioned.athleteVersions())
            + "|"
            + new TreeMap<>(versioned.refereeVersions());
    return "W/\"" + DigestUtils.md5DigestAsHex(versions.getBytes(StandardCharsets.UTF_8)) + "\"";
  }
}

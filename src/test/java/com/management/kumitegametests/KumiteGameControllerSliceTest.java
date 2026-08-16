package com.management.kumitegametests;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.exceptions.GameNotFoundException;
import com.management.exceptions.InvalidGameRequestException;
import com.management.exceptions.InvalidPlayerColorException;
import com.management.exceptions.PlayerNotFoundException;
import com.management.exceptions.PointTypeNotFoundException;
import com.management.models.KumiteGame;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.PlayerBuilder;
import com.management.testsupport.WebSliceTestBase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every endpoint on {@code /api/kumitegame}, at the HTTP boundary.
 *
 * <p>Started life in #87 as a two-test harness deliberately kept small until the response contract
 * settled; #37 filled it in once #33, #34, #35 and #36 had decided what the contract is. Each
 * endpoint has a success path asserting status and body, and each status the exception handler can
 * produce has a test.
 *
 * <p>Configuration comes from {@link WebSliceTestBase} — see there for why it is shared.
 */
class KumiteGameControllerSliceTest extends WebSliceTestBase {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KumiteGameService kumiteGameService;

  @MockitoBean private PlayerService playerService;

  @Test
  void createKumiteGame_whenTheRequestIsAccepted_returns201WithLocationHeader() throws Exception {
    given(kumiteGameService.createKumiteGame(any()))
        .willReturn(KumiteGameBuilder.newGame().alreadyPersistedAs("game-1").build());

    mockMvc
        .perform(
            post("/api/kumitegame")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "playersMap": {
                        "red":  { "name": "Kenji", "color": "RED" },
                        "blue": { "name": "Sato",  "color": "BLUE" }
                      },
                      "refereeList": ["Test Referee"],
                      "gameDuration": "90"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/kumitegame/game-1"))
        .andExpect(jsonPath("$.id").value("game-1"))
        .andExpect(jsonPath("$.gameState").value("QUEUED"));
  }

  @Test
  void getKumiteGame_whenTheGameExists_returns200AndTheGameAsJson() throws Exception {
    KumiteGame game =
        KumiteGameBuilder.newGame().runningWithRemaining(Duration.ofSeconds(87)).build();
    given(kumiteGameService.getKumiteGame("game-1")).willReturn(game);

    mockMvc
        .perform(get("/api/kumitegame/{gameId}", "game-1"))
        .andExpect(status().isOk())
        // Asserted at the wire level, not against the object. Object equality passes while the
        // serialised contract changes underneath -- which is the whole point of Epic #32.
        .andExpect(jsonPath("$.gameState").value("RUNNING"))
        .andExpect(jsonPath("$.remainingSeconds").value(87))
        .andExpect(jsonPath("$.red.name").value("Red Fighter"))
        .andExpect(jsonPath("$.blue.name").value("Blue Fighter"));
  }

  /**
   * The boundary rule, asserted as absence.
   *
   * <p>This is the only automated defence against a persistence type leaking back through the
   * controller. Every name below is a field of the stored document that a client must never see; if
   * one reappears, an entity is being serialised again.
   */
  @Test
  void getKumiteGame_response_carriesNoPersistenceDetail() throws Exception {
    KumiteGame game =
        KumiteGameBuilder.newGame().runningWithRemaining(Duration.ofSeconds(87)).build();
    given(kumiteGameService.getKumiteGame("game-1")).willReturn(game);

    mockMvc
        .perform(get("/api/kumitegame/{gameId}", "game-1"))
        .andExpect(status().isOk())
        // The colour-keyed map is internal structure; the response names red and blue.
        .andExpect(jsonPath("$.playersMap").doesNotExist())
        // Points and fouls are counts, not the wrapper objects the scoring strategies mutate.
        .andExpect(jsonPath("$.red.points").isNumber())
        .andExpect(jsonPath("$.red.fouls").isNumber())
        // Internal bookkeeping: the clock's origin, its total, and the transient timer.
        .andExpect(jsonPath("$.startTime").doesNotExist())
        .andExpect(jsonPath("$.gameDuration").doesNotExist())
        .andExpect(jsonPath("$.timer").doesNotExist());
  }

  /**
   * Pins the JSON representation, as the counterpart to {@link RepresentationCharacterizationTest}
   * which pins the stored form.
   *
   * <p>Asserted here rather than against a hand-built mapper because this is the only place the
   * application's real configured mapper runs — the one that will change under a dependency
   * upgrade.
   *
   * <p>Retargeted by #33 from the entity's shape to the response type's, and tightened by #35 from
   * a handful of paths to the whole body. What it pins is no longer "whatever the document happens
   * to look like" but a contract someone chose.
   *
   * <p><strong>Strict comparison, deliberately.</strong> A path-by-path assertion cannot notice a
   * field that appears — which is exactly how a persistence type leaks back in. Strict mode fails
   * on an unexpected field as well as a changed one, so this single assertion covers both
   * directions of the contract.
   *
   * <p><strong>The formats this locks (#35).</strong> {@code remainingSeconds} is a whole number of
   * seconds, not an ISO-8601 duration string and not a decimal. No timestamp appears at all — the
   * match's {@code startTime} is internal bookkeeping and a client holding {@code remainingSeconds}
   * has no use for it, so the cheapest way to specify its format was to not expose it. Enums are
   * their names. Points and fouls are bare integers, not the objects that carry them in storage.
   * Referees are names, not objects. Changing any of those breaks this test, which is the point.
   */
  @Test
  void getKumiteGame_whenTheMatchIsRunning_returnsExactlyThisBody() throws Exception {
    KumiteGame game =
        KumiteGameBuilder.newGame()
            .alreadyPersistedAs("game-1")
            .with(
                PlayerColor.RED,
                PlayerBuilder.newPlayer()
                    .alreadyPersistedAs("red-1")
                    .named("Kenji")
                    .scoring(PointsType.IPPON)
                    .build())
            .with(
                PlayerColor.BLUE,
                PlayerBuilder.newPlayer()
                    .alreadyPersistedAs("blue-1")
                    .named("Sato")
                    .withFouls(1)
                    .build())
            .runningWithRemaining(Duration.ofSeconds(87))
            .build();
    given(kumiteGameService.getKumiteGame("game-1")).willReturn(game);

    mockMvc
        .perform(get("/api/kumitegame/{gameId}", "game-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "id": "game-1",
                      "gameState": "RUNNING",
                      "remainingSeconds": 87,
                      "red":  { "id": "red-1",  "name": "Kenji", "points": 3, "fouls": 0 },
                      "blue": { "id": "blue-1", "name": "Sato",  "points": 0, "fouls": 1 },
                      "referees": ["Test Referee"],
                      "winner": null
                    }
                    """,
                    JsonCompareMode.STRICT));
  }

  /**
   * The decided match, pinning how a winner is encoded.
   *
   * <p>A colour name, not the sentence the field used to hold. The counterpart assertion — that an
   * undecided match reports {@code null} rather than a placeholder string — is in the running-match
   * body above.
   */
  @Test
  void getKumiteGame_whenTheMatchHasBeenWon_reportsTheWinningColour() throws Exception {
    KumiteGame game =
        KumiteGameBuilder.newGame()
            .alreadyPersistedAs("game-1")
            .finished()
            .wonBy(PlayerColor.RED)
            .build();
    given(kumiteGameService.getKumiteGame("game-1")).willReturn(game);

    mockMvc
        .perform(get("/api/kumitegame/{gameId}", "game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gameState").value("FINISHED"))
        .andExpect(jsonPath("$.remainingSeconds").value(0))
        .andExpect(jsonPath("$.winner").value("RED"));
  }

  /**
   * The four scoring endpoints, which all answer with the match as it now stands.
   *
   * <p>One test per endpoint rather than a parameterised sweep: each names a different service
   * call, and a failure should say which endpoint broke without decoding a parameter index.
   */
  @Test
  void addPoint_whenTheRequestIsValid_returns200WithTheUpdatedMatch() throws Exception {
    given(playerService.addPoint("game-1", "RED", "IPPON"))
        .willReturn(matchWhereRedHasScored(PointsType.IPPON));

    mockMvc
        .perform(
            put("/api/kumitegame/{gameId}/add-point", "game-1")
                .param("color", "RED")
                .param("pointType", "IPPON"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.red.points").value(3))
        .andExpect(jsonPath("$.blue.points").value(0));
  }

  @Test
  void removePoint_whenTheRequestIsValid_returns200WithTheUpdatedMatch() throws Exception {
    given(playerService.removePoint("game-1", "RED", "IPPON"))
        .willReturn(KumiteGameBuilder.newGame().alreadyPersistedAs("game-1").build());

    mockMvc
        .perform(
            put("/api/kumitegame/{gameId}/remove-point", "game-1")
                .param("color", "RED")
                .param("pointType", "IPPON"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.red.points").value(0));
  }

  @Test
  void addFoul_whenTheRequestIsValid_returns200WithTheUpdatedMatch() throws Exception {
    given(playerService.addFoul("game-1", "BLUE")).willReturn(matchWhereBlueHasFouled(1));

    mockMvc
        .perform(put("/api/kumitegame/{gameId}/add-foul", "game-1").param("color", "BLUE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blue.fouls").value(1));
  }

  @Test
  void removeFoul_whenTheRequestIsValid_returns200WithTheUpdatedMatch() throws Exception {
    given(playerService.removeFoul("game-1", "BLUE")).willReturn(matchWhereBlueHasFouled(0));

    mockMvc
        .perform(put("/api/kumitegame/{gameId}/remove-foul", "game-1").param("color", "BLUE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blue.fouls").value(0));
  }

  @Test
  void updateKumiteGameWinner_whenTheColourIsValid_returns200WithTheWinner() throws Exception {
    given(kumiteGameService.updateKumiteGameWinner("game-1", "RED"))
        .willReturn(
            KumiteGameBuilder.newGame()
                .alreadyPersistedAs("game-1")
                .finished()
                .wonBy(PlayerColor.RED)
                .build());

    mockMvc
        .perform(put("/api/kumitegame/{gameId}/update-winner/{color}", "game-1", "RED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.winner").value("RED"))
        .andExpect(jsonPath("$.gameState").value("FINISHED"));
  }

  private static KumiteGame matchWhereRedHasScored(PointsType pointsType) {
    return KumiteGameBuilder.newGame()
        .alreadyPersistedAs("game-1")
        .with(PlayerColor.RED, PlayerBuilder.newPlayer().named("Kenji").scoring(pointsType).build())
        .build();
  }

  private static KumiteGame matchWhereBlueHasFouled(int fouls) {
    return KumiteGameBuilder.newGame()
        .alreadyPersistedAs("game-1")
        .with(PlayerColor.BLUE, PlayerBuilder.newPlayer().named("Sato").withFouls(fouls).build())
        .build();
  }

  @Test
  void getKumiteGame_whenTheGameIsMissing_returns404AsProblemDetail() throws Exception {
    given(kumiteGameService.getKumiteGame(anyString()))
        .willThrow(new GameNotFoundException("Game not found! Game Id: nope"));

    mockMvc
        .perform(get("/api/kumitegame/{gameId}", "nope"))
        .andExpect(status().isNotFound())
        // The media type is half the contract: it tells a client the body is an error, in a
        // format it can parse without a bespoke agreement.
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Match not found"))
        .andExpect(jsonPath("$.detail").value("Game not found! Game Id: nope"));
  }

  /**
   * The status correction #36 exists for.
   *
   * <p>This assertion previously pinned the <em>wrong</em> behaviour on purpose — 404 for an
   * unparseable colour — so that fixing it would show up as a failing test rather than a silent
   * change. This is that change.
   */
  @Test
  void addPoint_whenTheColourIsNotRecognised_returns400() throws Exception {
    willThrow(new InvalidPlayerColorException("Unknown player colour: purple"))
        .given(playerService)
        .addPoint(anyString(), anyString(), anyString());

    mockMvc
        .perform(
            put("/api/kumitegame/{gameId}/add-point", "game-1")
                .param("color", "purple")
                .param("pointType", "IPPON"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid player colour"));
  }

  @Test
  void addPoint_whenThePointTypeIsNotRecognised_returns400() throws Exception {
    willThrow(new PointTypeNotFoundException("Unknown point type: triple-axel"))
        .given(playerService)
        .addPoint(anyString(), anyString(), anyString());

    mockMvc
        .perform(
            put("/api/kumitegame/{gameId}/add-point", "game-1")
                .param("color", "RED")
                .param("pointType", "triple-axel"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid point type"));
  }

  /**
   * A real colour that this match does not field stays 404.
   *
   * <p>Paired with the test above deliberately: the same parameter, two different failures, two
   * different statuses. That distinction is the whole point of keeping them separate exception
   * types.
   */
  @Test
  void addPoint_whenTheMatchDoesNotFieldThatColour_returns404() throws Exception {
    willThrow(new PlayerNotFoundException("Match game-1 has no BLUE fighter."))
        .given(playerService)
        .addPoint(anyString(), anyString(), anyString());

    mockMvc
        .perform(
            put("/api/kumitegame/{gameId}/add-point", "game-1")
                .param("color", "BLUE")
                .param("pointType", "IPPON"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Fighter not found"));
  }

  @Test
  void createKumiteGame_whenTheServiceRejectsTheRequest_returns400WithTheReason() throws Exception {
    given(kumiteGameService.createKumiteGame(any()))
        .willThrow(new InvalidGameRequestException("Players cannot be empty"));

    mockMvc
        .perform(
            post("/api/kumitegame")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playersMap\":{},\"refereeList\":[]}"))
        // Reached the catch-all and reported a client mistake as a server fault before #36.
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        // Echoed because this project wrote it: the caller can act on it and it says nothing
        // internal.
        .andExpect(jsonPath("$.detail").value("Players cannot be empty"));
  }

  /**
   * An illegal argument from somewhere below the controller: still 400, but never its message.
   *
   * <p>The blanket {@code IllegalArgumentException} handler catches exceptions raised inside the
   * framework and the driver as well as our own — Spring Data's "The given id must not be null" is
   * one. Echoing those blamed the caller for a server fault <em>and</em> handed them internal text.
   * The message it must not leak here is a database credential, the same thing the catch-all test
   * guards.
   */
  @Test
  void addPoint_whenAnUnexpectedIllegalArgumentEscapes_returns400WithoutLeakingTheMessage()
      throws Exception {
    willThrow(new IllegalArgumentException("invalid id for mongodb://user:secret@host/db"))
        .given(playerService)
        .addPoint(anyString(), anyString(), anyString());

    mockMvc
        .perform(
            put("/api/kumitegame/{gameId}/add-point", "game-1")
                .param("color", "RED")
                .param("pointType", "IPPON"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("The request contained an invalid value."))
        .andExpect(content().string(not(containsString("secret"))));
  }

  @Test
  void createKumiteGame_whenTheDurationIsNotNumeric_returns400() throws Exception {
    given(kumiteGameService.createKumiteGame(any()))
        .willThrow(new NumberFormatException("For input string: \"two minutes\""));

    mockMvc
        .perform(
            post("/api/kumitegame")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gameDuration\":\"two minutes\"}"))
        // NumberFormatException is an IllegalArgumentException, so one handler covers both.
        .andExpect(status().isBadRequest());
  }

  /**
   * The catch-all, which must not leak.
   *
   * <p>An unhandled exception's message can carry internal identifiers, query fragments or file
   * paths. The status is generic and so is the body; the message goes to the log instead.
   */
  @Test
  void getKumiteGame_whenSomethingUnexpectedFails_returns500WithoutLeakingTheMessage()
      throws Exception {
    given(kumiteGameService.getKumiteGame(anyString()))
        .willThrow(new IllegalStateException("connection string mongodb://user:secret@host/db"));

    mockMvc
        .perform(get("/api/kumitegame/{gameId}", "game-1"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
        .andExpect(content().string(not(containsString("secret"))));
  }

  /**
   * A framework-raised exception, mapped by the base class rather than by anything written here.
   *
   * <p>This is what extending {@code ResponseEntityExceptionHandler} buys. Before #36 an unreadable
   * body hit the catch-all and came back as 500 — the server blamed for a malformed request.
   */
  @Test
  void createKumiteGame_whenTheBodyIsNotReadable_returns400FromTheBaseHandler() throws Exception {
    mockMvc
        .perform(post("/api/kumitegame").contentType(MediaType.APPLICATION_JSON).content("{ not"))
        .andExpect(status().isBadRequest());
  }
}

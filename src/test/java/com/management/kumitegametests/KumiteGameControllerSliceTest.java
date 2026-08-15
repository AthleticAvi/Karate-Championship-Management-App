package com.management.kumitegametests;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.controllers.KumiteGameController;
import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.exceptions.GameNotFoundException;
import com.management.exceptions.GlobalExceptionHandler;
import com.management.models.KumiteGame;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.PlayerBuilder;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The web slice harness. Two exemplars, one per direction, and no more.
 *
 * <p><strong>This is a harness, not a test suite.</strong> Covering every endpoint belongs to #37,
 * and should follow the response-contract issues in Epic #32 rather than precede them — otherwise
 * it locks in a shape that is about to change.
 *
 * <p><strong>The context configuration #37 should reuse.</strong> {@code @WebMvcTest}, an
 * {@code @Import} naming the controller under test plus {@link GlobalExceptionHandler}, and every
 * collaborating service supplied as a {@code @MockitoBean}. Both services must be mocked even when
 * a test only exercises one, because the controller injects both and the context will not start
 * otherwise. Keeping every controller slice on this exact configuration means the framework builds
 * and caches one context for all of them; varying it per class multiplies context builds and
 * dominates suite time.
 *
 * <p><strong>Why the controller is imported rather than selected.</strong>
 * {@code @WebMvcTest(controllers = ...)} filters a component scan, and the configuration anchor
 * these tests resolve to declares no scan at all — see {@code
 * com.management.SliceTestConfiguration}. Without an import the controller bean is simply never
 * created and every request resolves to the static-resource handler, producing a confusing 500
 * instead of an obvious wiring failure. Importing the controller registers it directly.
 *
 * <p>Only the web layer starts here. There is no database, no service logic and no component scan,
 * which is what makes these tests cost milliseconds rather than seconds.
 */
@WebMvcTest
@Import({KumiteGameController.class, GlobalExceptionHandler.class})
class KumiteGameControllerSliceTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KumiteGameService kumiteGameService;

  @MockitoBean private PlayerService playerService;

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

  @Test
  void getKumiteGame_whenTheGameIsMissing_returns404FromTheExceptionHandler() throws Exception {
    given(kumiteGameService.getKumiteGame(anyString()))
        .willThrow(new GameNotFoundException("Game not found! Game Id: nope"));

    mockMvc
        .perform(get("/api/kumitegame/{gameId}", "nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value("Game not found! Game Id: nope"));
  }

  @Test
  void addPoint_withAnUnknownColour_returns404_currentBehaviour() throws Exception {
    willThrow(new com.management.exceptions.InvalidPlayerColorException("Invalid color: PURPLE"))
        .given(playerService)
        .addPoint(anyString(), anyString(), anyString());

    mockMvc
        .perform(
            put("/api/kumitegame/{gameId}/add-point", "game-1")
                .param("color", "PURPLE")
                .param("pointType", "IPPON"))
        // Asserts CURRENT behaviour deliberately, not intended behaviour. An invalid colour is bad
        // input and should be 400; it returns 404 today. #36 changes this, and this assertion is
        // what will make that change visible rather than silent.
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}

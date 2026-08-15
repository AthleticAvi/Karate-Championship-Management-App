package com.management.kumitegametests;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.controllers.KumiteGameController;
import com.management.exceptions.GameNotFoundException;
import com.management.exceptions.GlobalExceptionHandler;
import com.management.models.KumiteGame;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import com.management.testsupport.KumiteGameBuilder;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
   * <p>Retargeted by #33 from the entity's shape to the response type's. What it pins is no longer
   * "whatever the document happens to look like" but a contract someone chose.
   */
  @Test
  void getKumiteGame_jsonRepresentation_isPinned() throws Exception {
    KumiteGame game =
        KumiteGameBuilder.newGame().runningWithRemaining(Duration.ofSeconds(87)).build();
    given(kumiteGameService.getKumiteGame("game-1")).willReturn(game);

    mockMvc
        .perform(get("/api/kumitegame/{gameId}", "game-1"))
        .andExpect(status().isOk())
        // The clock is a whole number of seconds, not an ISO-8601 duration string.
        .andExpect(jsonPath("$.remainingSeconds").value(87))
        // Points and fouls are bare numbers, not objects carrying a count.
        .andExpect(jsonPath("$.red.points").value(0))
        .andExpect(jsonPath("$.red.fouls").value(0))
        // Enums serialise as their names.
        .andExpect(jsonPath("$.gameState").value("RUNNING"))
        // Referees are a list of names, not objects.
        .andExpect(jsonPath("$.referees[0]").value("Test Referee"))
        // A match with no winner reports no winner, rather than a sentence saying so (#34).
        .andExpect(jsonPath("$.winner").value(nullValue()));
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

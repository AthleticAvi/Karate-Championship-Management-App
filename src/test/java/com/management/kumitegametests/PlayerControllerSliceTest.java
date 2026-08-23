package com.management.kumitegametests;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.enums.PointsType;
import com.management.exceptions.PlayerNotFoundException;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import com.management.testsupport.PlayerBuilder;
import com.management.testsupport.WebSliceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every endpoint on {@code /api/players}, at the HTTP boundary.
 *
 * <p>Configuration comes from {@link WebSliceTestBase}, and the mocked beans match {@link
 * KumiteGameControllerSliceTest} exactly so both classes share one cached context.
 */
class PlayerControllerSliceTest extends WebSliceTestBase {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KumiteGameService kumiteGameService;

  @MockitoBean private PlayerService playerService;

  @Test
  void createPlayer_whenTheRequestIsValid_returns201WithLocationHeader() throws Exception {
    given(playerService.createPlayer(any()))
        .willReturn(
            PlayerBuilder.newPlayer().alreadyPersistedAs("player-1").named("Kenji").build());

    mockMvc
        .perform(
            post("/api/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Kenji\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/players/player-1"))
        .andExpect(
            content()
                .json(
                    """
                    { "id": "player-1", "name": "Kenji", "points": 0, "fouls": 0 }
                    """,
                    JsonCompareMode.STRICT));
  }

  /**
   * The fighter body, pinned strictly.
   *
   * <p>Strict comparison is what asserts the absence of everything the stored document carries and
   * the response must not: the {@code Points} and {@code Foul} wrapper objects that used to nest
   * these counts, and anything added to {@code Player} in future.
   */
  @Test
  void getPlayer_whenTheFighterExists_returnsExactlyThisBody() throws Exception {
    given(playerService.getPlayer("player-1"))
        .willReturn(
            PlayerBuilder.newPlayer()
                .alreadyPersistedAs("player-1")
                .named("Kenji")
                .scoring(PointsType.WAZARI)
                .withFouls(2)
                .build());

    mockMvc
        .perform(get("/api/players/{playerId}", "player-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    { "id": "player-1", "name": "Kenji", "points": 2, "fouls": 2 }
                    """,
                    JsonCompareMode.STRICT));
  }

  /**
   * Bean Validation on the fighter body (#39): a blank name is rejected at the boundary, naming the
   * field, and the service is never consulted.
   */
  @Test
  void createPlayer_whenTheNameIsBlank_returns400NamingTheField() throws Exception {
    mockMvc
        .perform(
            post("/api/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.errors.name").value("a fighter needs a name"));

    then(playerService).shouldHaveNoInteractions();
  }

  @Test
  void getPlayer_whenTheFighterIsMissing_returns404AsProblemDetail() throws Exception {
    given(playerService.getPlayer(anyString()))
        .willThrow(new PlayerNotFoundException("Player not found Player ID: nope"));

    mockMvc
        .perform(get("/api/players/{playerId}", "nope"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Fighter not found"));
  }

  @Test
  void deletePlayer_whenTheFighterExists_returns204WithNoBody() throws Exception {
    mockMvc
        .perform(delete("/api/players/{playerId}", "player-1"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    then(playerService).should().deletePlayer("player-1");
  }

  @Test
  void deletePlayer_whenTheFighterIsMissing_returns404() throws Exception {
    willThrow(new PlayerNotFoundException("Player not found Player ID: nope"))
        .given(playerService)
        .deletePlayer("nope");

    mockMvc
        .perform(delete("/api/players/{playerId}", "nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}

package com.management.kumitegametests;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.athlete.domain.Athlete;
import com.management.kumitegame.domain.ActionRefusedException;
import com.management.kumitegame.domain.ClockActionKind;
import com.management.kumitegame.domain.GameChangedException;
import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.GameNotFoundException;
import com.management.kumitegame.domain.GameRead;
import com.management.kumitegame.domain.InvalidGameRequestException;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.NewClockAction;
import com.management.kumitegame.domain.NewGame;
import com.management.kumitegame.domain.NewResult;
import com.management.kumitegame.domain.NewScore;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.PanelMember;
import com.management.kumitegame.domain.PanelRole;
import com.management.kumitegame.domain.Recording;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.ScoreSource;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.Timer;
import com.management.kumitegame.domain.Warning;
import com.management.referee.domain.Referee;
import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import com.management.testsupport.WebSliceTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every endpoint on {@code /api/v1/kumite-games}, at the HTTP boundary, and the wire format pinned
 * whole: a strict comparison is the only thing between this contract and a silent shift under a
 * dependency upgrade. Who may call each is proven in {@code SecurityRulesSliceTest}.
 */
@WithMockUser(username = "kenta", roles = "SCOREKEEPER")
class KumiteGameControllerSliceTest extends WebSliceTestBase {

  private static final String GAMES = "/api/v1/kumite-games";
  private static final String GAME = GAMES + "/{gameId}";
  private static final Instant LATER = TestClock.NOW.plusSeconds(2);

  @Autowired private MockMvc mockMvc;

  /**
   * Started, with one WAZA_ARI to AKA. The AO athlete no longer exists: the accepted race with a
   * delete, which reads as unknown.
   */
  private static GameRead scored() {
    KumiteGame game = TestGames.game();
    game.recordClockAction(
        new NewClockAction("c1", Timer.MAIN, null, ClockActionKind.START, 120_000, null, null),
        TestGames.SCOREKEEPER,
        TestClock.NOW);
    game.recordScore(
        new NewScore("s1", Side.AKA, ScoreLevel.WAZA_ARI, ScoreSource.JUDGES, null, 118_000L),
        TestGames.SCOREKEEPER,
        LATER);
    return new GameRead(
        game,
        game.state(),
        Map.of(TestGames.AKA_ATHLETE, new Athlete(TestGames.AKA_ATHLETE, "Kenji", 61.5)),
        Map.of(TestGames.REFEREE, new Referee(TestGames.REFEREE, "Sato")),
        LATER);
  }

  private static final String META_CREATED =
      """
      "recordedAt": "2026-01-01T12:00:00Z", "recordedBy": "scorekeeper", "raisedWarnings": []
      """;

  private static final String SCORED_GAME =
      """
      {
        "id": "game-1", "format": "ELIMINATION", "medalGame": false, "extraGame": false,
        "settings": {
          "durationMs": 120000, "durationsOfferedMs": [90000, 120000, 180000],
          "pointValues": {"IPPON": 3, "WAZA_ARI": 2, "YUKO": 1},
          "leadToWin": 8, "finalWindowMs": 15000, "lateScoreWindowMs": 1500, "chuiLimit": 3,
          "roundRobinForfeitPoints": 4, "addTimeStepsMs": [10000, 20000, 30000],
          "medicalTimeMs": 180000, "timeShownToleranceMs": 1000, "rulebookVersion": "WKF 2026.01"
        },
        "status": "FIGHTING", "exchange": 1,
        "mainClock": {"running": true, "remainingMs": 118000},
        "aka": {
          "colour": "red",
          "athlete": {"id": "athlete-aka", "name": "Kenji", "known": true},
          "medicalClock": {"running": false, "remainingMs": 180000},
          "total": 2, "ipponCount": 0, "wazaAriCount": 1, "chuiCount": 0, "hansokuChuiCount": 0,
          "scores": [{
            "level": "WAZA_ARI", "source": "JUDGES", "exchange": 1, "clockRemainingMs": 118000,
            "cancelled": false,
            "meta": {"id": "s1", "order": 5, "recordedAt": "2026-01-01T12:00:02Z",
                     "recordedBy": "scorekeeper", "raisedWarnings": []}
          }],
          "penalties": []
        },
        "ao": {
          "colour": "blue",
          "athlete": {"id": "athlete-ao", "name": null, "known": false},
          "medicalClock": {"running": false, "remainingMs": 180000},
          "total": 0, "ipponCount": 0, "wazaAriCount": 0, "chuiCount": 0, "hansokuChuiCount": 0,
          "scores": [], "penalties": []
        },
        "panel": [{"referee": {"id": "referee-1", "name": "Sato", "known": true}, "role": "REFEREE"}],
        "assignments": [
          {"kind": "ATHLETE", "side": "AKA", "athleteId": "athlete-aka", "refereeId": null,
           "role": null, "meta": {"id": "created-1", "order": 1, %1$s}},
          {"kind": "ATHLETE", "side": "AO", "athleteId": "athlete-ao", "refereeId": null,
           "role": null, "meta": {"id": "created-2", "order": 2, %1$s}},
          {"kind": "PANEL_ADD", "side": null, "athleteId": null, "refereeId": "referee-1",
           "role": "REFEREE", "meta": {"id": "created-3", "order": 3, %1$s}}
        ],
        "clockActions": [
          {"timer": "MAIN", "side": null, "action": "START", "timeShownMs": 120000,
           "amountMs": null, "pauseReason": null, "meta": {"id": "c1", "order": 4, %1$s}}
        ],
        "amendments": [], "results": [], "currentResult": null, "warnings": [],
        "serverTime": "2026-01-01T12:00:02Z"
      }
      """
          .formatted(META_CREATED);

  @Test
  void get_returnsExactlyTheGame() throws Exception {
    given(kumiteGameService().get("game-1")).willReturn(scored());

    mockMvc
        .perform(get(GAME, "game-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(SCORED_GAME, JsonCompareMode.STRICT));
  }

  @Test
  void create_returns201WithLocation_theGameAndItsWarnings_recordedByTheSignedInAccount()
      throws Exception {
    NewGame expected =
        new NewGame(
            GameFormat.ROUND_ROBIN,
            true,
            false,
            "athlete-aka",
            "athlete-ao",
            List.of(new PanelMember("referee-1", PanelRole.REFEREE)));
    given(kumiteGameService().create(expected, "kenta"))
        .willReturn(new Recording(scored(), List.of(Warning.FORMAT_COMBINATION)));

    mockMvc
        .perform(
            post(GAMES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"format": "ROUND_ROBIN", "medalGame": true, "extraGame": false,
                     "akaAthleteId": "athlete-aka", "aoAthleteId": "athlete-ao",
                     "panel": [{"refereeId": "referee-1", "role": "REFEREE"}]}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", GAMES + "/game-1"))
        .andExpect(
            content()
                .json(
                    "{\"game\": " + SCORED_GAME + ", \"raisedWarnings\": [\"FORMAT_COMBINATION\"]}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void score_isMappedWhole_andAnswersTheGame() throws Exception {
    NewScore expected =
        new NewScore("s2", Side.AO, ScoreLevel.IPPON, ScoreSource.VIDEO_REVIEW, 1, 90_000L);
    given(kumiteGameService().recordScore("game-1", expected, "kenta"))
        .willReturn(new Recording(scored(), List.of(Warning.CLOCK_REMAINING_OUT_OF_TOLERANCE)));

    mockMvc
        .perform(
            post(GAME + "/scores", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id": "s2", "side": "AO", "level": "IPPON", "source": "VIDEO_REVIEW",
                     "exchange": 1, "clockRemainingMs": 90000}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.raisedWarnings[0]").value("CLOCK_REMAINING_OUT_OF_TOLERANCE"))
        .andExpect(jsonPath("$.game.id").value("game-1"));
  }

  /** The recorder comes from the signed-in account: a body that names one is not read. */
  @Test
  void result_takesTheRecorderFromTheAccount() throws Exception {
    NewResult expected =
        new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.HANTEI, null, false);
    given(kumiteGameService().recordResult("game-1", expected, "kenta"))
        .willReturn(new Recording(scored(), List.of()));

    mockMvc
        .perform(
            post(GAME + "/results", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id": "r1", "outcome": "WIN", "winner": "AKA", "reason": "HANTEI",
                     "bothDisqualified": false, "recordedBy": "someone-else"}
                    """))
        .andExpect(status().isOk());

    then(kumiteGameService()).should().recordResult("game-1", expected, "kenta");
  }

  @Test
  void entryWithoutId_isRefusedNamingTheField() throws Exception {
    mockMvc
        .perform(
            post(GAME + "/penalties", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"side\": \"AKA\", \"level\": \"CHUI\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors.id").exists());

    then(kumiteGameService()).shouldHaveNoInteractions();
  }

  @Test
  void settingsWithZeroDuration_isRefused() throws Exception {
    mockMvc
        .perform(
            put(GAME + "/settings", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"durationMs": 0, "durationsOfferedMs": [120000],
                     "pointValues": {"IPPON": 3, "WAZA_ARI": 2, "YUKO": 1}, "leadToWin": 8,
                     "finalWindowMs": 15000, "lateScoreWindowMs": 1500, "chuiLimit": 3,
                     "roundRobinForfeitPoints": 4, "addTimeStepsMs": [10000],
                     "medicalTimeMs": 180000, "timeShownToleranceMs": 1000}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.durationMs").exists());

    then(kumiteGameService()).shouldHaveNoInteractions();
  }

  @Test
  void refusedAction_isConflict_withTheReason() throws Exception {
    given(kumiteGameService().recordScore(eq("game-1"), any(), anyString()))
        .willThrow(new ActionRefusedException("Entry id s1 was already used."));

    mockMvc
        .perform(
            post(GAME + "/scores", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"id\": \"s1\", \"side\": \"AO\", \"level\": \"YUKO\", \"source\": \"JUDGES\"}"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Action refused"))
        .andExpect(jsonPath("$.detail").value("Entry id s1 was already used."));
  }

  /** The current game travels in the problem detail, so the caller needs no second read. */
  @Test
  void absoluteActionThatLost_isConflict_withTheCurrentGame() throws Exception {
    given(kumiteGameService().recordResult(eq("game-1"), any(), anyString()))
        .willThrow(
            new GameChangedException(scored(), new OptimisticLockingFailureException("lost")));

    mockMvc
        .perform(
            post(GAME + "/results", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id": "r1", "outcome": "DRAW", "reason": "HIKIWAKE",
                     "bothDisqualified": false}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Concurrent update"))
        .andExpect(jsonPath("$.current.id").value("game-1"))
        .andExpect(jsonPath("$.current.aka.colour").value("red"))
        .andExpect(jsonPath("$.current.aka.total").value(2));
  }

  @Test
  void invalidCombination_isBadRequest_withTheReason() throws Exception {
    given(kumiteGameService().recordResult(eq("game-1"), any(), anyString()))
        .willThrow(new InvalidGameRequestException("A WIN names a winner."));

    mockMvc
        .perform(
            post(GAME + "/results", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id": "r1", "outcome": "WIN", "reason": "POINTS", "bothDisqualified": false}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("A WIN names a winner."));
  }

  @Test
  void missingGame_isNotFound() throws Exception {
    given(kumiteGameService().get(anyString()))
        .willThrow(new GameNotFoundException("No game has the id nope"));

    mockMvc
        .perform(get(GAME, "nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Game not found"));
  }
}

package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.athlete.domain.Athlete;
import com.management.kumitegame.domain.ActingRole;
import com.management.kumitegame.domain.ActionForbiddenException;
import com.management.kumitegame.domain.ActionRefusedException;
import com.management.kumitegame.domain.AmendmentKind;
import com.management.kumitegame.domain.ClockActionKind;
import com.management.kumitegame.domain.GameChangedException;
import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.GameNotFoundException;
import com.management.kumitegame.domain.GameRead;
import com.management.kumitegame.domain.GameStatus;
import com.management.kumitegame.domain.InvalidGameRequestException;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.NewAmendment;
import com.management.kumitegame.domain.NewClockAction;
import com.management.kumitegame.domain.NewGame;
import com.management.kumitegame.domain.NewPenalty;
import com.management.kumitegame.domain.NewResult;
import com.management.kumitegame.domain.NewScore;
import com.management.kumitegame.domain.NewSenshuRecord;
import com.management.kumitegame.domain.NewStatusChange;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.PanelMember;
import com.management.kumitegame.domain.PanelRole;
import com.management.kumitegame.domain.PauseReason;
import com.management.kumitegame.domain.PenaltyLevel;
import com.management.kumitegame.domain.Recording;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.ScoreSource;
import com.management.kumitegame.domain.SenshuRecordKind;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.StatusChangeKind;
import com.management.kumitegame.domain.Timer;
import com.management.kumitegame.domain.Warning;
import com.management.kumitegame.service.VersionedGameRead;
import com.management.referee.domain.Referee;
import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import com.management.testsupport.WebSliceTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Every endpoint on {@code /api/v1/kumite-games}, at the HTTP boundary, and the wire format pinned
 * whole: a strict comparison is the only thing between this contract and a silent shift under a
 * dependency upgrade. Who may call each is proven in {@code SecurityRulesSliceTest}.
 */
@WithMockUser(username = "kenta", roles = "SCOREKEEPER")
class KumiteGameControllerSliceTest extends WebSliceTestBase {

  private static final String GAMES = "/api/v1/kumite-games";
  private static final String GAME = GAMES + "/{gameId}";
  private static final String FULL_VIEW = GAME + "/full-view";
  private static final Instant LATER = TestClock.NOW.plusSeconds(2);

  /** The stored version every game here is read at. */
  private static final long VERSION = 7;

  @Autowired private MockMvc mockMvc;

  /**
   * Started, with one WAZA_ARI and SENSHU to AKA. The AO athlete no longer exists: the accepted
   * race with a delete, which reads as unknown.
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
    game.recordSenshu(
        new NewSenshuRecord("k1", Side.AKA, SenshuRecordKind.AWARDED, null, null, 118_000L),
        TestGames.SCOREKEEPER,
        LATER);
    return new GameRead(
        game,
        VERSION,
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
        "id": "game-1", "version": 7, "format": "ELIMINATION", "medalGame": false,
        "extraGame": false,
        "settings": {
          "durationMs": 120000, "durationsOfferedMs": [90000, 120000, 180000],
          "pointValues": {"IPPON": 3, "WAZA_ARI": 2, "YUKO": 1},
          "leadToWin": 8, "finalWindowMs": 15000, "lateScoreWindowMs": 1500, "chuiLimit": 3,
          "roundRobinForfeitPoints": 4, "addTimeStepsMs": [10000, 20000, 30000],
          "medicalTimeMs": 180000, "timeShownToleranceMs": 1000, "panelLimit": 10,
          "rulebookVersion": "WKF 2026.01"
        },
        "status": "FIGHTING", "exchange": 1,
        "mainClock": {"running": true, "remainingMs": 118000},
        "aka": {
          "colour": "red",
          "athlete": {"id": "athlete-aka", "name": "Kenji", "known": true},
          "senshu": true,
          "medicalClock": {"running": false, "remainingMs": 180000},
          "total": 2, "ipponCount": 0, "wazaAriCount": 1, "chuiCount": 0, "hansokuChuiCount": 0,
          "hansokuCount": 0, "shikkakuCount": 0, "disqualified": false,
          "effectiveScore": {"total": 2, "ipponCount": 0, "wazaAriCount": 1, "addedPoints": 0},
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
          "senshu": false,
          "medicalClock": {"running": false, "remainingMs": 180000},
          "total": 0, "ipponCount": 0, "wazaAriCount": 0, "chuiCount": 0, "hansokuChuiCount": 0,
          "hansokuCount": 0, "shikkakuCount": 0, "disqualified": false,
          "effectiveScore": {"total": 0, "ipponCount": 0, "wazaAriCount": 0, "addedPoints": 0},
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
        "senshuClosed": false,
        "senshuRecords": [
          {"side": "AKA", "kind": "AWARDED", "reason": null, "exchange": 1,
           "clockRemainingMs": 118000, "cancelled": false,
           "meta": {"id": "k1", "order": 6, "recordedAt": "2026-01-01T12:00:02Z",
                    "recordedBy": "scorekeeper", "raisedWarnings": []}}
        ],
        "amendments": [], "results": [], "currentResult": null, "suggestedResult": null,
        "statusChanges": [],
        "warnings": [],
        "serverTime": "2026-01-01T12:00:02Z"
      }
      """
          .formatted(META_CREATED);

  private static String tagFrom(ResultActions answer) {
    String tag = answer.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    assertThat(tag).as("the answer carries a tag").isNotNull();
    return Objects.requireNonNull(tag);
  }

  /** The game at fixed versions: the game's, one athlete's and one referee's. */
  private static VersionedGameRead versioned(GameRead read) {
    return versioned(() -> read);
  }

  /** The same versions, with a fold the test can watch. */
  private static VersionedGameRead versioned(Supplier<GameRead> fold) {
    return new VersionedGameRead(
        VERSION, Map.of(TestGames.AKA_ATHLETE, 3L), Map.of(TestGames.REFEREE, 2L), fold);
  }

  /**
   * The scorekeeper's view written out, less who recorded each entry, every warning and every note.
   * A note is removed list by list, because a result's {@code reason} is an enum and stays.
   */
  private static String publicView(String fullView) {
    ObjectNode game = (ObjectNode) JsonMapper.shared().readTree(fullView);
    game.remove("warnings");
    game.findParents("recordedBy").forEach(meta -> ((ObjectNode) meta).remove("recordedBy"));
    game.findParents("raisedWarnings")
        .forEach(meta -> ((ObjectNode) meta).remove("raisedWarnings"));
    game.findParents("reasonDetail")
        .forEach(result -> ((ObjectNode) result).remove("reasonDetail"));
    for (String noted : List.of("senshuRecords", "amendments", "statusChanges")) {
      game.get(noted).forEach(entry -> ((ObjectNode) entry).remove("reason"));
    }
    return game.toString();
  }

  /**
   * Every note in free text filled in, and the enums a scoreboard shows: paused for an injury,
   * SENSHU awarded then cancelled, decided by KIKEN and approved, its result still standing.
   */
  private static GameRead noted() {
    KumiteGame game = TestGames.game();
    game.recordClockAction(
        new NewClockAction("c1", Timer.MAIN, null, ClockActionKind.START, 120_000, null, null),
        TestGames.SCOREKEEPER,
        TestClock.NOW);
    game.recordClockAction(
        new NewClockAction(
            "c2", Timer.MAIN, null, ClockActionKind.PAUSE, 100_000, null, PauseReason.INJURY),
        TestGames.SCOREKEEPER,
        LATER);
    game.recordSenshu(
        new NewSenshuRecord(
            "k1", Side.AKA, SenshuRecordKind.AWARDED, "first unopposed score", null, 100_000L),
        TestGames.SCOREKEEPER,
        LATER);
    game.recordAmendment(
        new NewAmendment("m1", AmendmentKind.CANCEL, "k1", "AO scored in the same exchange"),
        TestGames.SCOREKEEPER,
        LATER);
    game.recordResult(
        new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.KIKEN, "AO concussed", false),
        TestGames.SCOREKEEPER,
        LATER);
    game.recordStatusChange(
        new NewStatusChange("z1", StatusChangeKind.APPROVE, "signed off by the Kansa"),
        ActingRole.SCOREKEEPER,
        TestGames.SCOREKEEPER,
        LATER);
    return new GameRead(game, VERSION, game.state(), Map.of(), Map.of(), LATER);
  }

  /**
   * Decided by KIKEN before the start, with a reason in free text. The AO athlete no longer exists.
   */
  private static GameRead decided() {
    KumiteGame game = TestGames.game();
    game.recordResult(
        new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.KIKEN, "AO could not go on", false),
        TestGames.SCOREKEEPER,
        LATER);
    return new GameRead(
        game,
        VERSION,
        game.state(),
        Map.of(TestGames.AKA_ATHLETE, new Athlete(TestGames.AKA_ATHLETE, "Kenji", 61.5)),
        Map.of(TestGames.REFEREE, new Referee(TestGames.REFEREE, "Sato")),
        LATER);
  }

  /**
   * Anyone may list games, so a summary holds nothing the public view leaves out: no recorder, no
   * warning, and no free text, such as a result's detail.
   */
  @Test
  @WithAnonymousUser
  void list_answersSummaries_withNoRecorderWarningOrFreeText() throws Exception {
    given(
            kumiteGameService()
                .list(
                    Set.of(GameStatus.DECIDED, GameStatus.APPROVED),
                    TestGames.AKA_ATHLETE,
                    PageRequest.of(0, 20)))
        .willReturn(new PageImpl<>(List.of(decided()), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(
            get(GAMES)
                .param("status", "DECIDED", "APPROVED")
                .param("athleteId", TestGames.AKA_ATHLETE))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "content": [{
                        "id": "game-1", "status": "DECIDED",
                        "aka": {
                          "colour": "red",
                          "athlete": {"id": "athlete-aka", "name": "Kenji", "known": true}
                        },
                        "ao": {
                          "colour": "blue",
                          "athlete": {"id": "athlete-ao", "name": null, "known": false}
                        },
                        "currentResult": {
                          "outcome": "WIN", "winner": "AKA", "reason": "KIKEN",
                          "bothDisqualified": false
                        }
                      }],
                      "page": {"size": 20, "number": 0, "totalElements": 1, "totalPages": 1}
                    }
                    """,
                    JsonCompareMode.STRICT));
  }

  @Test
  void list_withNoFilter_asksForEveryStatusAndAnyAthlete() throws Exception {
    given(kumiteGameService().list(Set.of(), null, PageRequest.of(2, 3)))
        .willReturn(new PageImpl<>(List.of(), PageRequest.of(2, 3), 0));

    mockMvc
        .perform(get(GAMES).param("page", "2").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    then(kumiteGameService()).should().list(Set.of(), null, PageRequest.of(2, 3));
  }

  @Test
  void list_withStatusThatDoesNotExist_isRefusedAsBadRequest() throws Exception {
    mockMvc
        .perform(get(GAMES).param("status", "PAUSED"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.detail").value("The value of status is not one it accepts."))
        .andExpect(content().string(not(containsString("PAUSED"))));

    then(kumiteGameService()).shouldHaveNoInteractions();
  }

  @Test
  void fullView_returnsExactlyTheGame() throws Exception {
    given(kumiteGameService().getVersioned("game-1")).willReturn(versioned(scored()));

    mockMvc
        .perform(get(FULL_VIEW, "game-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(SCORED_GAME, JsonCompareMode.STRICT));
  }

  /** The same game, without who recorded what, warnings or notes, and still the server's time. */
  @Test
  @WithAnonymousUser
  void publicView_returnsTheGame_withNoRecorderWarningsOrNotes() throws Exception {
    given(kumiteGameService().getVersioned("game-1")).willReturn(versioned(scored()));

    mockMvc
        .perform(get(GAME, "game-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(publicView(SCORED_GAME), JsonCompareMode.STRICT))
        .andExpect(content().string(not(containsString("recordedBy"))))
        .andExpect(content().string(not(containsString("raisedWarnings"))))
        .andExpect(content().string(not(containsString("\"warnings\""))))
        .andExpect(jsonPath("$.serverTime").value("2026-01-01T12:00:02Z"));
  }

  /**
   * A game stored before the panel limit existed has none, and says so in both views. It is built
   * as a stored game is read, because only a stored game can lack the limit.
   */
  @ParameterizedTest
  @ValueSource(strings = {GAME, FULL_VIEW})
  void read_ofGameWithNoPanelLimit_answersItEmpty(String view) throws Exception {
    KumiteGame game =
        new KumiteGame(
            TestGames.GAME_ID,
            GameFormat.ELIMINATION,
            false,
            false,
            TestGames.settings(120_000, null),
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            TestGames.rules());
    given(kumiteGameService().getVersioned("game-1"))
        .willReturn(
            versioned(new GameRead(game, VERSION, game.state(), Map.of(), Map.of(), LATER)));

    mockMvc
        .perform(get(view, "game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.settings.panelLimit").value(nullValue()));
  }

  /**
   * A note can hold a medical detail or why a game was voided, so anyone may follow the game but
   * not read the notes. The enums stay: a scoreboard shows them.
   */
  @Test
  @WithAnonymousUser
  void publicView_leavesOutEveryNote_andKeepsTheEnums() throws Exception {
    given(kumiteGameService().getVersioned("game-1")).willReturn(versioned(noted()));

    mockMvc
        .perform(get(GAME, "game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.senshuRecords[0].reason").doesNotExist())
        .andExpect(jsonPath("$.amendments[0].reason").doesNotExist())
        .andExpect(jsonPath("$.results[0].reasonDetail").doesNotExist())
        .andExpect(jsonPath("$.currentResult.reasonDetail").doesNotExist())
        .andExpect(jsonPath("$.statusChanges[0].reason").doesNotExist())
        .andExpect(jsonPath("$.results[0].reason").value("KIKEN"))
        .andExpect(jsonPath("$.currentResult.reason").value("KIKEN"))
        .andExpect(jsonPath("$.clockActions[1].pauseReason").value("INJURY"));
  }

  @Test
  void fullView_keepsEveryNote() throws Exception {
    given(kumiteGameService().getVersioned("game-1")).willReturn(versioned(noted()));

    mockMvc
        .perform(get(FULL_VIEW, "game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.senshuRecords[0].reason").value("first unopposed score"))
        .andExpect(jsonPath("$.amendments[0].reason").value("AO scored in the same exchange"))
        .andExpect(jsonPath("$.results[0].reasonDetail").value("AO concussed"))
        .andExpect(jsonPath("$.currentResult.reasonDetail").value("AO concussed"))
        .andExpect(jsonPath("$.statusChanges[0].reason").value("signed off by the Kansa"));
  }

  @ParameterizedTest
  @ValueSource(strings = {GAME, FULL_VIEW})
  void read_withTheCurrentTag_isNotModified_withNoBody(String view) throws Exception {
    given(kumiteGameService().getVersioned("game-1")).willReturn(versioned(scored()));
    String tag = tagFrom(mockMvc.perform(get(view, "game-1")).andExpect(status().isOk()));

    mockMvc
        .perform(get(view, "game-1").header(HttpHeaders.IF_NONE_MATCH, tag))
        .andExpect(status().isNotModified())
        .andExpect(header().string(HttpHeaders.ETAG, tag))
        .andExpect(content().string(""));
    assertThat(tag).startsWith("W/\"");
  }

  /**
   * An unchanged game costs only the reads of its versions: the history is not folded, so no body
   * can be built either.
   */
  @ParameterizedTest
  @ValueSource(strings = {GAME, FULL_VIEW})
  void read_withTheCurrentTag_neverFoldsTheGame(String view) throws Exception {
    AtomicInteger folds = new AtomicInteger();
    given(kumiteGameService().getVersioned("game-1"))
        .willReturn(
            versioned(() -> {
              folds.incrementAndGet();
              return scored();
            }));
    String tag = tagFrom(mockMvc.perform(get(view, "game-1")).andExpect(status().isOk()));
    assertThat(folds).as("a full answer folds once").hasValue(1);

    mockMvc
        .perform(get(view, "game-1").header(HttpHeaders.IF_NONE_MATCH, tag))
        .andExpect(status().isNotModified());

    assertThat(folds).as("a 304 never folds").hasValue(1);
  }

  /**
   * A browser keeps the public view and asks with its tag before showing it again. A cache takes a
   * 304's headers in place of those it held, so the 304 carries the same policy.
   */
  @Test
  @WithAnonymousUser
  void publicView_mayBeKept_butIsAlwaysAskedForAgain() throws Exception {
    given(kumiteGameService().getVersioned("game-1")).willReturn(versioned(scored()));
    String tag =
        tagFrom(
            mockMvc
                .perform(get(GAME, "game-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache")));

    mockMvc
        .perform(get(GAME, "game-1").header(HttpHeaders.IF_NONE_MATCH, tag))
        .andExpect(status().isNotModified())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
  }

  /** Who recorded what is never kept, by a browser or by a cache between. */
  @Test
  void fullView_isNeverStored() throws Exception {
    given(kumiteGameService().getVersioned("game-1")).willReturn(versioned(scored()));

    mockMvc
        .perform(get(FULL_VIEW, "game-1"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
  }

  /** A tag from one view never stands for the other, so a client never keeps the wrong body. */
  @Test
  void tag_differsBetweenTheViews_soOneViewsTagGetsTheOtherInFull() throws Exception {
    given(kumiteGameService().getVersioned("game-1")).willReturn(versioned(noted()));
    String publicTag = tagFrom(mockMvc.perform(get(GAME, "game-1")));

    mockMvc
        .perform(get(FULL_VIEW, "game-1").header(HttpHeaders.IF_NONE_MATCH, publicTag))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results[0].meta.recordedBy").value("scorekeeper"))
        .andExpect(jsonPath("$.statusChanges[0].reason").value("signed off by the Kansa"));
  }

  /** The other way round: a client that read the full view never keeps its notes in public. */
  @Test
  void tag_fromTheFullView_getsThePublicViewInFull_withNoNotes() throws Exception {
    given(kumiteGameService().getVersioned("game-1")).willReturn(versioned(noted()));
    String fullTag = tagFrom(mockMvc.perform(get(FULL_VIEW, "game-1")));

    mockMvc
        .perform(get(GAME, "game-1").header(HttpHeaders.IF_NONE_MATCH, fullTag))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusChanges[0].kind").value("APPROVE"))
        .andExpect(jsonPath("$.statusChanges[0].reason").doesNotExist());
  }

  /**
   * A standing HANSOKU or SHIKKAKU disqualifies its side, in both views, so a display screen shows
   * it without replaying the penalties. AO's stands; AKA's was cancelled, so AKA is not out.
   */
  @ParameterizedTest
  @CsvSource({
    "HANSOKU, " + GAME + ", 1, 0",
    "HANSOKU, " + FULL_VIEW + ", 1, 0",
    "SHIKKAKU, " + GAME + ", 0, 1",
    "SHIKKAKU, " + FULL_VIEW + ", 0, 1"
  })
  void side_isDisqualified_whileItsHansokuOrShikkakuStands_inBothViews(
      PenaltyLevel level, String view, int hansokuCount, int shikkakuCount) throws Exception {
    KumiteGame game = TestGames.game();
    game.recordPenalty(
        new NewPenalty("p1", Side.AO, level, null, null, null), TestGames.SCOREKEEPER, LATER);
    game.recordPenalty(
        new NewPenalty("p2", Side.AKA, level, null, null, null), TestGames.SCOREKEEPER, LATER);
    game.recordAmendment(
        new NewAmendment("m1", AmendmentKind.CANCEL, "p2", null), TestGames.SCOREKEEPER, LATER);
    given(kumiteGameService().getVersioned("game-1"))
        .willReturn(
            versioned(new GameRead(game, VERSION, game.state(), Map.of(), Map.of(), LATER)));

    mockMvc
        .perform(get(view, "game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ao.disqualified").value(true))
        .andExpect(jsonPath("$.ao.hansokuCount").value(hansokuCount))
        .andExpect(jsonPath("$.ao.shikkakuCount").value(shikkakuCount))
        .andExpect(jsonPath("$.aka.penalties[0].cancelled").value(true))
        .andExpect(jsonPath("$.aka.disqualified").value(false))
        .andExpect(jsonPath("$.aka.hansokuCount").value(0))
        .andExpect(jsonPath("$.aka.shikkakuCount").value(0));
  }

  /** Both at once: each is counted, so a screen can say by what. */
  @Test
  void side_withHansokuAndShikkaku_countsEach() throws Exception {
    KumiteGame game = TestGames.game();
    game.recordPenalty(
        new NewPenalty("p1", Side.AO, PenaltyLevel.HANSOKU, null, null, null),
        TestGames.SCOREKEEPER,
        LATER);
    game.recordPenalty(
        new NewPenalty("p2", Side.AO, PenaltyLevel.SHIKKAKU, null, null, null),
        TestGames.SCOREKEEPER,
        LATER);
    given(kumiteGameService().getVersioned("game-1"))
        .willReturn(
            versioned(new GameRead(game, VERSION, game.state(), Map.of(), Map.of(), LATER)));

    mockMvc
        .perform(get(GAME, "game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ao.hansokuCount").value(1))
        .andExpect(jsonPath("$.ao.shikkakuCount").value(1))
        .andExpect(jsonPath("$.ao.disqualified").value(true));
  }

  /**
   * A SHIKKAKU given off the mat is often only the result: the loser is disqualified in both views,
   * with no penalty entry behind it.
   */
  @ParameterizedTest
  @ValueSource(strings = {GAME, FULL_VIEW})
  void side_isDisqualified_byTheResultThatStands_inBothViews(String view) throws Exception {
    KumiteGame game = TestGames.game();
    game.recordResult(
        new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.SHIKKAKU, null, false),
        TestGames.SCOREKEEPER,
        LATER);
    given(kumiteGameService().getVersioned("game-1"))
        .willReturn(
            versioned(new GameRead(game, VERSION, game.state(), Map.of(), Map.of(), LATER)));

    mockMvc
        .perform(get(view, "game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ao.disqualified").value(true))
        .andExpect(jsonPath("$.ao.hansokuCount").value(0))
        .andExpect(jsonPath("$.ao.shikkakuCount").value(0))
        .andExpect(jsonPath("$.aka.disqualified").value(false));
  }

  /** Each version the answer shows is in the tag: one changed version answers the body again. */
  @Test
  void read_withStaleTag_answersTheGameAndTheNewTag() throws Exception {
    VersionedGameRead before = versioned(scored());
    VersionedGameRead renamed =
        new VersionedGameRead(
            VERSION,
            Map.of(TestGames.AKA_ATHLETE, 4L),
            Map.of(TestGames.REFEREE, 2L),
            before.fold());
    given(kumiteGameService().getVersioned("game-1")).willReturn(before, renamed);
    String stale = tagFrom(mockMvc.perform(get(GAME, "game-1")));

    String current =
        tagFrom(
            mockMvc
                .perform(get(GAME, "game-1").header(HttpHeaders.IF_NONE_MATCH, stale))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("game-1")));
    assertThat(current).isNotEqualTo(stale);
  }

  /**
   * At time up, AKA ahead on points: the live suggestion, and the one a different result was
   * recorded against, kept on it.
   */
  @Test
  void get_showsTheSuggestion_andTheSuggestionEachResultWasRecordedAgainst() throws Exception {
    GameRead read = scored();
    KumiteGame game = read.game();
    game.recordClockAction(
        new NewClockAction("c2", Timer.MAIN, null, ClockActionKind.TIME_UP, 0, null, null),
        TestGames.SCOREKEEPER,
        LATER);
    game.recordResult(
        new NewResult("r1", Outcome.WIN, Side.AO, ResultReason.HANTEI, null, false),
        TestGames.SCOREKEEPER,
        LATER);
    given(kumiteGameService().getVersioned("game-1"))
        .willReturn(
            versioned(new GameRead(game, VERSION, game.state(), Map.of(), Map.of(), LATER)));

    Map<String, Object> points =
        Map.of("outcome", "WIN", "winner", "AKA", "reason", "POINTS", "bothDisqualified", false);
    mockMvc
        .perform(get(FULL_VIEW, "game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suggestedResult", equalTo(points)))
        .andExpect(jsonPath("$.results[0].suggested", equalTo(points)))
        .andExpect(
            jsonPath("$.results[0].meta.raisedWarnings[0]")
                .value("RESULT_DIFFERS_FROM_SUGGESTION"));
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

  @Test
  void senshu_isMappedWhole_andAnswersTheGame() throws Exception {
    NewSenshuRecord expected =
        new NewSenshuRecord(
            "k2", Side.AKA, SenshuRecordKind.WITHDRAWN, "avoiding combat", 1, 12_000L);
    given(kumiteGameService().recordSenshu("game-1", expected, "kenta"))
        .willReturn(new Recording(scored(), List.of(Warning.SENSHU_WITHDRAWN_NOT_HELD)));

    mockMvc
        .perform(
            post(GAME + "/senshu-records", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id": "k2", "side": "AKA", "kind": "WITHDRAWN", "reason": "avoiding combat",
                     "exchange": 1, "clockRemainingMs": 12000}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.raisedWarnings[0]").value("SENSHU_WITHDRAWN_NOT_HELD"))
        .andExpect(jsonPath("$.game.aka.senshu").value(true));
  }

  @Test
  void senshuWithoutKind_isRefusedNamingTheField() throws Exception {
    mockMvc
        .perform(
            post(GAME + "/senshu-records", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"k2\", \"side\": \"AKA\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.kind").exists());

    then(kumiteGameService()).shouldHaveNoInteractions();
  }

  /** The recorder comes from the signed-in account. */
  @Test
  void result_takesTheRecorderFromTheAccount() throws Exception {
    NewResult expected =
        new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.HANTEI, null, false);
    given(kumiteGameService().recordResult("game-1", expected, VERSION, "kenta"))
        .willReturn(new Recording(scored(), List.of()));

    mockMvc
        .perform(
            post(GAME + "/results", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id": "r1", "outcome": "WIN", "winner": "AKA", "reason": "HANTEI",
                     "bothDisqualified": false, "expectedVersion": 7}
                    """))
        .andExpect(status().isOk());

    then(kumiteGameService()).should().recordResult("game-1", expected, VERSION, "kenta");
  }

  /** No body has room for a recorder, so one that names one is refused, naming the field. */
  @Test
  void result_namingTheRecorder_isRefusedNamingTheField() throws Exception {
    mockMvc
        .perform(
            post(GAME + "/results", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id": "r1", "outcome": "WIN", "winner": "AKA", "reason": "HANTEI",
                     "bothDisqualified": false, "recordedBy": "someone-else",
                     "expectedVersion": 7}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.errors.recordedBy").value("is not a field of this request"))
        .andExpect(jsonPath("$.errors.length()").value(1));

    then(kumiteGameService()).shouldHaveNoInteractions();
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
                     "medicalTimeMs": 180000, "timeShownToleranceMs": 1000,
                     "expectedVersion": 7}
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
    given(kumiteGameService().recordResult(eq("game-1"), any(), anyLong(), anyString()))
        .willThrow(
            new GameChangedException(scored(), new OptimisticLockingFailureException("lost")));

    mockMvc
        .perform(
            post(GAME + "/results", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id": "r1", "outcome": "DRAW", "reason": "HIKIWAKE",
                     "bothDisqualified": false, "expectedVersion": 6}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Concurrent update"))
        .andExpect(jsonPath("$.current.id").value("game-1"))
        .andExpect(jsonPath("$.current.version").value(VERSION))
        .andExpect(jsonPath("$.current.aka.colour").value("red"))
        .andExpect(jsonPath("$.current.aka.total").value(2));
  }

  @Test
  void invalidCombination_isBadRequest_withTheReason() throws Exception {
    given(kumiteGameService().recordResult(eq("game-1"), any(), anyLong(), anyString()))
        .willThrow(new InvalidGameRequestException("A WIN names a winner."));

    mockMvc
        .perform(
            post(GAME + "/results", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id": "r1", "outcome": "WIN", "reason": "POINTS", "bothDisqualified": false,
                     "expectedVersion": 7}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("A WIN names a winner."));
  }

  /** A voided game, as a mutation answers it. */
  private static Recording voided() {
    KumiteGame game = TestGames.game();
    game.recordStatusChange(
        new NewStatusChange("v1", StatusChangeKind.VOID, "never fought"),
        ActingRole.ADMIN,
        "aiko",
        LATER);
    return new Recording(
        new GameRead(game, VERSION, game.state(), Map.of(), Map.of(), LATER), List.of());
  }

  @Test
  void statusChange_byScorekeeper_isMappedWhole_withTheScorekeeperRole() throws Exception {
    NewStatusChange expected = new NewStatusChange("z1", StatusChangeKind.APPROVE, null);
    given(
            kumiteGameService()
                .recordStatusChange("game-1", expected, VERSION, ActingRole.SCOREKEEPER, "kenta"))
        .willReturn(voided());

    mockMvc
        .perform(
            post(GAME + "/status-changes", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"z1\", \"kind\": \"APPROVE\", \"expectedVersion\": 7}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.game.status").value("VOIDED"))
        .andExpect(jsonPath("$.game.currentResult").isEmpty())
        .andExpect(
            content()
                .json(
                    """
                    {"game": {"statusChanges": [
                      {"kind": "VOID", "reason": "never fought",
                       "meta": {"id": "v1", "order": 4, "recordedAt": "2026-01-01T12:00:02Z",
                                "recordedBy": "aiko", "raisedWarnings": []}}
                    ]}}
                    """,
                    JsonCompareMode.LENIENT));

    then(kumiteGameService())
        .should()
        .recordStatusChange("game-1", expected, VERSION, ActingRole.SCOREKEEPER, "kenta");
  }

  /** The role comes from the signed-in account, by the account role's name. */
  @Test
  @WithMockUser(username = "aiko", roles = "ADMIN")
  void statusChange_byAdmin_passesTheAdminRole() throws Exception {
    NewStatusChange expected = new NewStatusChange("v1", StatusChangeKind.VOID, "protest accepted");
    given(
            kumiteGameService()
                .recordStatusChange("game-1", expected, VERSION, ActingRole.ADMIN, "aiko"))
        .willReturn(voided());

    mockMvc
        .perform(
            post(GAME + "/status-changes", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id": "v1", "kind": "VOID", "reason": "protest accepted",
                     "expectedVersion": 7}
                    """))
        .andExpect(status().isOk());

    then(kumiteGameService())
        .should()
        .recordStatusChange("game-1", expected, VERSION, ActingRole.ADMIN, "aiko");
  }

  /** The role table admits the endpoint, so the game refuses the move with the same 403. */
  @Test
  void adminOnlyMove_fromScorekeeper_isForbidden_withTheReason() throws Exception {
    given(
            kumiteGameService()
                .recordStatusChange(eq("game-1"), any(), anyLong(), any(), anyString()))
        .willThrow(new ActionForbiddenException("Only an Admin may void a game that is DECIDED."));

    mockMvc
        .perform(
            post(GAME + "/status-changes", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"id\": \"v1\", \"kind\": \"VOID\", \"reason\": \"protest\", \"expectedVersion\": 7}"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Only an Admin may void a game that is DECIDED."));
  }

  @Test
  void statusChangeWithoutKind_isRefusedNamingTheField() throws Exception {
    mockMvc
        .perform(
            post(GAME + "/status-changes", "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"z1\", \"expectedVersion\": 7}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.kind").exists());

    then(kumiteGameService()).shouldHaveNoInteractions();
  }

  @Test
  void missingGame_isNotFound() throws Exception {
    given(kumiteGameService().getVersioned(anyString()))
        .willThrow(new GameNotFoundException("No game has the id nope"));

    mockMvc
        .perform(get(GAME, "nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Game not found"));
    mockMvc.perform(get(FULL_VIEW, "nope")).andExpect(status().isNotFound());
  }

  /** Every absolute action needs the version it was chosen against: none is refused unread. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "PUT|settings|{\"durationMs\": 90000, \"durationsOfferedMs\": [90000], \"pointValues\":"
            + " {\"IPPON\": 3, \"WAZA_ARI\": 2, \"YUKO\": 1}, \"leadToWin\": 8, \"finalWindowMs\":"
            + " 15000, \"lateScoreWindowMs\": 1500, \"chuiLimit\": 3, \"roundRobinForfeitPoints\": 4,"
            + " \"addTimeStepsMs\": [10000], \"medicalTimeMs\": 180000, \"timeShownToleranceMs\":"
            + " 1000}",
        "POST|results|{\"id\": \"r1\", \"outcome\": \"DRAW\", \"reason\": \"HIKIWAKE\","
            + " \"bothDisqualified\": false}",
        "POST|status-changes|{\"id\": \"z1\", \"kind\": \"APPROVE\"}"
      })
  void absoluteActionWithoutVersion_isRefusedNamingTheField(
      String method, String resource, String body) throws Exception {
    mockMvc
        .perform(
            request(HttpMethod.valueOf(method), GAME + "/" + resource, "game-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.expectedVersion").exists());

    then(kumiteGameService()).shouldHaveNoInteractions();
  }
}

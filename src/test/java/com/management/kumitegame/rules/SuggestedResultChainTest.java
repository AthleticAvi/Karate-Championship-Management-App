package com.management.kumitegame.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.ResultRules;
import com.management.kumitegame.domain.Settings;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.SideState;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import com.management.testsupport.TestGames;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The suggested-result chain, each step in each format, as the container orders it: the chain is
 * {@link TestGames#rules()}, built from the beans and their {@code @Order}, never a list written
 * out here.
 */
class SuggestedResultChainTest {

  private static final ResultRules CHAIN = TestGames.rules();

  /** A format, and whether the game is a medal game or an extra game. */
  enum Game {
    ELIMINATION(GameFormat.ELIMINATION, false, false),
    ELIMINATION_MEDAL(GameFormat.ELIMINATION, true, false),
    ROUND_ROBIN(GameFormat.ROUND_ROBIN, false, false),
    ROUND_ROBIN_EXTRA(GameFormat.ROUND_ROBIN, false, true);

    final GameFormat format;
    final boolean medal;
    final boolean extra;

    Game(GameFormat format, boolean medal, boolean extra) {
      this.format = format;
      this.medal = medal;
      this.extra = extra;
    }
  }

  private static SideState side(int total, int ippon, int wazaAri, int hansoku, int shikkaku) {
    return new SideState("athlete", total, ippon, wazaAri, 0, 0, hansoku, shikkaku);
  }

  private static SideState scored(int total, int ippon, int wazaAri) {
    return side(total, ippon, wazaAri, 0, 0);
  }

  private static Standing standing(
      Game game,
      boolean timeUp,
      SideState aka,
      SideState ao,
      @Nullable Side senshu,
      Settings settings) {
    return new Standing(
        game.format,
        game.medal,
        game.extra,
        settings,
        timeUp,
        Map.of(Side.AKA, aka, Side.AO, ao),
        senshu);
  }

  private static Optional<SuggestedResult> suggest(
      Game game, boolean timeUp, SideState aka, SideState ao, @Nullable Side senshu) {
    return CHAIN.suggest(standing(game, timeUp, aka, ao, senshu, TestGames.settings()));
  }

  private static SuggestedResult win(Side winner, ResultReason reason) {
    return SuggestedResult.win(winner, reason);
  }

  @ParameterizedTest
  @EnumSource(Game.class)
  void hansokuAgainstOneSide_theOtherWins_byHansoku(Game game) {
    assertThat(suggest(game, false, scored(6, 2, 0), side(0, 0, 0, 1, 0), null))
        .contains(win(Side.AKA, ResultReason.HANSOKU));
  }

  @ParameterizedTest
  @EnumSource(Game.class)
  void shikkakuAgainstOneSide_theOtherWins_byShikkaku_evenWithHansokuToo(Game game) {
    assertThat(suggest(game, true, side(9, 3, 0, 1, 1), scored(0, 0, 0), null))
        .contains(win(Side.AO, ResultReason.SHIKKAKU));
  }

  /** Articles 12.2.11 and 10.7.1: no result, the next-round opponents win by bye. */
  @ParameterizedTest
  @EnumSource(
      value = Game.class,
      names = {"ELIMINATION", "ROUND_ROBIN", "ROUND_ROBIN_EXTRA"})
  void bothDisqualified_outsideMedalGame_isNoResult(Game game) {
    assertThat(suggest(game, true, side(6, 2, 0, 1, 0), side(0, 0, 0, 0, 1), Side.AKA))
        .contains(
            new SuggestedResult(
                Outcome.NO_RESULT, null, ResultReason.DOUBLE_DISQUALIFICATION, true));
  }

  /** Article 12.2.11: points, then SENSHU, then direct HANTEI, the disqualifications kept. */
  @Test
  void bothDisqualified_inMedalGame_isDecidedByPoints_thenSenshu_thenHantei() {
    Game medal = Game.ELIMINATION_MEDAL;
    assertThat(suggest(medal, false, side(1, 0, 0, 1, 0), side(3, 1, 0, 1, 0), Side.AKA))
        .contains(win(Side.AO, ResultReason.POINTS).withBothDisqualified());
    assertThat(suggest(medal, false, side(3, 1, 0, 1, 0), side(3, 0, 1, 1, 0), Side.AO))
        .contains(win(Side.AO, ResultReason.SENSHU).withBothDisqualified());
    // Level on points with no SENSHU: IPPON and WAZA_ARI are not asked, it is direct HANTEI.
    assertThat(suggest(medal, false, side(3, 1, 0, 1, 0), side(3, 0, 1, 0, 1), null))
        .contains(SuggestedResult.hantei().withBothDisqualified());
  }

  /** A round-robin medal game makes no sense, but the flag is what the rule reads. */
  @Test
  void bothDisqualified_inRoundRobinMedalGame_isDecidedAsMedalGame() {
    Standing standing =
        new Standing(
            GameFormat.ROUND_ROBIN,
            true,
            false,
            TestGames.settings(),
            false,
            Map.of(Side.AKA, side(2, 0, 1, 1, 0), Side.AO, side(0, 0, 0, 1, 0)),
            null);
    assertThat(CHAIN.suggest(standing))
        .contains(win(Side.AKA, ResultReason.POINTS).withBothDisqualified());
  }

  @ParameterizedTest
  @EnumSource(Game.class)
  void leadOfTheLeadToWin_wins_whileFighting(Game game) {
    assertThat(suggest(game, false, scored(1, 0, 0), scored(9, 3, 0), null))
        .contains(win(Side.AO, ResultReason.LEAD));
    assertThat(suggest(game, false, scored(8, 0, 4), scored(1, 0, 0), null)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(Game.class)
  void leadOfTheLeadToWin_atTimeUp_isLead_notPoints(Game game) {
    assertThat(suggest(game, true, scored(8, 2, 1), scored(0, 0, 0), null))
        .contains(win(Side.AKA, ResultReason.LEAD));
  }

  @ParameterizedTest
  @EnumSource(Game.class)
  void disqualification_comesBeforeTheLead(Game game) {
    assertThat(suggest(game, false, scored(0, 0, 0), side(9, 3, 0, 1, 0), null))
        .contains(win(Side.AKA, ResultReason.HANSOKU));
  }

  /** The chain reads the lead from the game's snapshot, whatever configuration says. */
  @Test
  void lead_isReadFromTheSettingsSnapshot() {
    Settings defaults = TestGames.settings();
    Settings leadOfThree =
        new Settings(
            defaults.durationMs(),
            defaults.durationsOfferedMs(),
            defaults.pointValues(),
            3,
            defaults.finalWindowMs(),
            defaults.lateScoreWindowMs(),
            defaults.chuiLimit(),
            defaults.roundRobinForfeitPoints(),
            defaults.addTimeStepsMs(),
            defaults.medicalTimeMs(),
            defaults.timeShownToleranceMs(),
            defaults.panelLimit(),
            defaults.rulebookVersion());
    assertThat(
            CHAIN.suggest(
                standing(
                    Game.ELIMINATION, false, scored(3, 1, 0), scored(0, 0, 0), null, leadOfThree)))
        .contains(win(Side.AKA, ResultReason.LEAD));
  }

  @ParameterizedTest
  @EnumSource(Game.class)
  void withoutLeadOrDisqualification_beforeTimeUp_suggestsNothing(Game game) {
    assertThat(suggest(game, false, scored(3, 1, 0), scored(0, 0, 0), Side.AKA)).isEmpty();
    assertThat(suggest(game, false, scored(0, 0, 0), scored(0, 0, 0), null)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(Game.class)
  void atTimeUp_morePointsWin_overSenshu(Game game) {
    assertThat(suggest(game, true, scored(2, 0, 1), scored(3, 1, 0), Side.AKA))
        .contains(win(Side.AO, ResultReason.POINTS));
  }

  @ParameterizedTest
  @EnumSource(Game.class)
  void atTimeUp_levelOnPoints_senshuWins_overIppon(Game game) {
    assertThat(suggest(game, true, scored(3, 0, 1), scored(3, 1, 0), Side.AKA))
        .contains(win(Side.AKA, ResultReason.SENSHU));
  }

  @ParameterizedTest
  @EnumSource(Game.class)
  void atTimeUp_levelWithNoSenshu_moreIpponWin(Game game) {
    assertThat(suggest(game, true, scored(3, 0, 1), scored(3, 1, 0), null))
        .contains(win(Side.AO, ResultReason.IPPON));
  }

  @ParameterizedTest
  @EnumSource(Game.class)
  void atTimeUp_ipponLevel_moreWazaAriWin(Game game) {
    assertThat(suggest(game, true, scored(2, 0, 1), scored(2, 0, 0), null))
        .contains(win(Side.AKA, ResultReason.WAZA_ARI));
  }

  /** Articles 12.2.4, 12.2.5, Appendix 6: HANTEI decides, and the rules never name its winner. */
  @ParameterizedTest
  @EnumSource(
      value = Game.class,
      names = {"ELIMINATION", "ELIMINATION_MEDAL", "ROUND_ROBIN_EXTRA"})
  void atTimeUp_stillLevel_isHantei_inEliminationAndExtraGame(Game game) {
    assertThat(suggest(game, true, scored(1, 0, 0), scored(1, 0, 0), null))
        .contains(new SuggestedResult(Outcome.WIN, null, ResultReason.HANTEI, false));
  }

  @Test
  void atTimeUp_stillLevel_isDraw_inRoundRobin() {
    assertThat(suggest(Game.ROUND_ROBIN, true, scored(0, 0, 0), scored(0, 0, 0), null))
        .contains(new SuggestedResult(Outcome.DRAW, null, ResultReason.HIKIWAKE, false));
  }

  @Test
  void formatWithoutRules_stopsTheStart() {
    assertThatThrownBy(() -> new LevelRule(List.of(new EliminationRules())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Every format needs its rules");
  }
}

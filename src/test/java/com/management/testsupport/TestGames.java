package com.management.testsupport;

import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.NewGame;
import com.management.kumitegame.domain.PanelMember;
import com.management.kumitegame.domain.PanelRole;
import com.management.kumitegame.domain.ResultRules;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.Settings;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Games for the fast suite, each built fresh: no test shares an instance with another.
 *
 * <p>The settings are the configured defaults written out, so a test reads the numbers it relies on
 * rather than the configuration it may be changing.
 */
public final class TestGames {

  public static final String GAME_ID = "game-1";
  public static final String AKA_ATHLETE = "athlete-aka";
  public static final String AO_ATHLETE = "athlete-ao";
  public static final String REFEREE = "referee-1";
  public static final String SCOREKEEPER = "scorekeeper";

  /**
   * The real suggested-result chain, built by the container from the rules package, so its order is
   * the {@code @Order} the beans declare, never a list written out here. Built once: it holds no
   * state.
   */
  private static final ResultRules RULES;

  static {
    try (AnnotationConfigApplicationContext rules =
        new AnnotationConfigApplicationContext("com.management.kumitegame.rules")) {
      RULES = rules.getBean(ResultRules.class);
    }
  }

  private TestGames() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  /**
   * WKF 2026.01 defaults: 2 minutes, 3/2/1, lead of 8, 1 second tolerance, 1.5 s late window, and a
   * panel of at most 10.
   */
  public static Settings settings() {
    return settings(120_000);
  }

  public static Settings settings(long durationMs) {
    return settings(durationMs, 10);
  }

  /** The defaults with this duration and panel limit, or none, as a game stored before it had. */
  public static Settings settings(long durationMs, @Nullable Integer panelLimit) {
    return new Settings(
        durationMs,
        List.of(90_000L, 120_000L, 180_000L),
        Map.of(ScoreLevel.IPPON, 3, ScoreLevel.WAZA_ARI, 2, ScoreLevel.YUKO, 1),
        8,
        15_000,
        1_500,
        3,
        4,
        List.of(10_000L, 20_000L, 30_000L),
        180_000,
        1_000,
        panelLimit,
        "WKF 2026.01");
  }

  public static NewGame newGame(GameFormat format, boolean medalGame, boolean extraGame) {
    return new NewGame(
        format,
        medalGame,
        extraGame,
        AKA_ATHLETE,
        AO_ATHLETE,
        List.of(new PanelMember(REFEREE, PanelRole.REFEREE)));
  }

  /** The real result rules, ordered as the application orders them. */
  public static ResultRules rules() {
    return RULES;
  }

  /** An elimination game, not started, with an athlete on each side and one referee. */
  public static KumiteGame game() {
    return game(GameFormat.ELIMINATION, false, false);
  }

  /** A game of this format, not started, with an athlete on each side and one referee. */
  public static KumiteGame game(GameFormat format, boolean medalGame, boolean extraGame) {
    return KumiteGame.create(
        GAME_ID,
        newGame(format, medalGame, extraGame),
        settings(),
        ids("created"),
        SCOREKEEPER,
        TestClock.NOW,
        RULES);
  }

  /** Ids that differ from each other and from every id a test names. */
  public static Supplier<String> ids(String prefix) {
    AtomicInteger next = new AtomicInteger();
    return () -> prefix + "-" + next.incrementAndGet();
  }
}

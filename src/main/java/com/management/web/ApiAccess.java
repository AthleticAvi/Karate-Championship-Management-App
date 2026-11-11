package com.management.web;

import com.management.account.web.AccountController;
import com.management.athlete.web.AthleteController;
import com.management.kumitegame.web.KumiteGameController;
import com.management.referee.web.RefereeController;
import java.util.List;
import org.springframework.http.HttpMethod;

/**
 * Who may call each endpoint: the one statement of the role table.
 *
 * <p>The filter chain builds its rules from this, and the API description reads it for the failures
 * and the scheme each operation states, so the two cannot disagree. It lives here because the
 * description may only sit in this package, and the {@code security} package already depends on
 * this one. Anything not listed is refused, whoever asks.
 */
public final class ApiAccess {

  /** {@link #SCOREKEEPER} also admits an Admin. */
  public enum Level {
    PUBLIC,
    SCOREKEEPER,
    ADMIN
  }

  /** One endpoint. The path is the mapped pattern, as the description also names it. */
  public record Rule(HttpMethod method, String path, Level level) {}

  private static final String GAMES = ApiVersionConfiguration.PREFIX + KumiteGameController.PATH;
  private static final String ONE_GAME = GAMES + "/{gameId}";
  private static final String ATHLETES = ApiVersionConfiguration.PREFIX + AthleteController.PATH;
  private static final String ONE_ATHLETE = ATHLETES + "/{athleteId}";
  private static final String REFEREES = ApiVersionConfiguration.PREFIX + RefereeController.PATH;
  private static final String ONE_REFEREE = REFEREES + "/{refereeId}";
  private static final String ACCOUNTS = ApiVersionConfiguration.PREFIX + AccountController.PATH;

  /**
   * A game is read by a Scorekeeper or an Admin: the public view of a game is a later addition.
   * Athletes follow the referee levels.
   */
  public static final List<Rule> RULES =
      List.of(
          new Rule(HttpMethod.GET, ONE_ATHLETE, Level.PUBLIC),
          new Rule(HttpMethod.GET, ONE_REFEREE, Level.PUBLIC),
          new Rule(HttpMethod.POST, GAMES, Level.SCOREKEEPER),
          new Rule(HttpMethod.GET, ONE_GAME, Level.SCOREKEEPER),
          new Rule(HttpMethod.PUT, ONE_GAME + "/settings", Level.SCOREKEEPER),
          new Rule(HttpMethod.POST, ONE_GAME + "/scores", Level.SCOREKEEPER),
          new Rule(HttpMethod.POST, ONE_GAME + "/penalties", Level.SCOREKEEPER),
          new Rule(HttpMethod.POST, ONE_GAME + "/clock-actions", Level.SCOREKEEPER),
          new Rule(HttpMethod.POST, ONE_GAME + "/senshu-records", Level.SCOREKEEPER),
          new Rule(HttpMethod.POST, ONE_GAME + "/assignments", Level.SCOREKEEPER),
          new Rule(HttpMethod.POST, ONE_GAME + "/amendments", Level.SCOREKEEPER),
          new Rule(HttpMethod.POST, ONE_GAME + "/results", Level.SCOREKEEPER),
          new Rule(HttpMethod.POST, ATHLETES, Level.ADMIN),
          new Rule(HttpMethod.PUT, ONE_ATHLETE, Level.ADMIN),
          new Rule(HttpMethod.DELETE, ONE_ATHLETE, Level.ADMIN),
          new Rule(HttpMethod.POST, REFEREES, Level.ADMIN),
          new Rule(HttpMethod.PUT, ONE_REFEREE, Level.ADMIN),
          new Rule(HttpMethod.DELETE, ONE_REFEREE, Level.ADMIN),
          new Rule(HttpMethod.POST, ACCOUNTS, Level.ADMIN),
          new Rule(HttpMethod.DELETE, ACCOUNTS + "/{username}", Level.ADMIN));

  private ApiAccess() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  static Level levelOf(String method, String path) {
    return RULES.stream()
        .filter(rule -> rule.method().name().equals(method) && rule.path().equals(path))
        .findFirst()
        .map(Rule::level)
        .orElseThrow(() -> new IllegalStateException("No access rule for " + method + " " + path));
  }
}

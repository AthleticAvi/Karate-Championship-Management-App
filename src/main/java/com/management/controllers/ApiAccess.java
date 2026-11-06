package com.management.controllers;

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

  private static final String GAME = ApiVersionConfiguration.PREFIX + KumiteGameController.PATH;
  private static final String ONE_GAME = GAME + "/{gameId}";
  private static final String PLAYERS = ApiVersionConfiguration.PREFIX + PlayerController.PATH;
  private static final String ONE_PLAYER = PLAYERS + "/{playerId}";
  private static final String ACCOUNTS = ApiVersionConfiguration.PREFIX + AccountController.PATH;

  public static final List<Rule> RULES =
      List.of(
          new Rule(HttpMethod.GET, ONE_GAME, Level.PUBLIC),
          new Rule(HttpMethod.GET, ONE_PLAYER, Level.PUBLIC),
          new Rule(HttpMethod.POST, GAME, Level.SCOREKEEPER),
          new Rule(HttpMethod.PUT, ONE_GAME + "/add-point", Level.SCOREKEEPER),
          new Rule(HttpMethod.PUT, ONE_GAME + "/remove-point", Level.SCOREKEEPER),
          new Rule(HttpMethod.PUT, ONE_GAME + "/add-foul", Level.SCOREKEEPER),
          new Rule(HttpMethod.PUT, ONE_GAME + "/remove-foul", Level.SCOREKEEPER),
          new Rule(HttpMethod.PUT, ONE_GAME + "/kiken/{color}", Level.SCOREKEEPER),
          new Rule(HttpMethod.PUT, ONE_GAME + "/add-time", Level.SCOREKEEPER),
          new Rule(HttpMethod.PUT, ONE_GAME + "/disqualify/{color}", Level.ADMIN),
          new Rule(HttpMethod.PUT, ONE_GAME + "/override-winner", Level.ADMIN),
          new Rule(HttpMethod.PUT, ONE_GAME + "/update-winner/{color}", Level.ADMIN),
          new Rule(HttpMethod.POST, PLAYERS, Level.ADMIN),
          new Rule(HttpMethod.DELETE, ONE_PLAYER, Level.ADMIN),
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

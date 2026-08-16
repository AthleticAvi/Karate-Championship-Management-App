package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.PlayerBuilder;
import org.junit.jupiter.api.Test;

/**
 * Two fixtures from one builder share nothing.
 *
 * <p><strong>Why this class exists.</strong> #85 required that no builder return a shared or cached
 * instance, and both builders appeared to satisfy it: every {@code build()} really did return a new
 * {@code Player} or {@code KumiteGame}. What they shared was one level down. {@code PlayerBuilder}
 * held a single {@code Points} and a single {@code Foul} and passed those same two references into
 * every player it built, and {@code Player} stores them without copying — so scoring against one
 * fixture silently moved another's score, and {@code KumiteGameBuilder} copied its map while
 * sharing the fighters inside it.
 *
 * <p>Nothing in the suite could fail because of it: no test built two fixtures from one builder and
 * then compared them. That is the shape of defect this epic was written to eliminate, so it gets an
 * explicit test rather than a fixed builder and a promise.
 */
class FixtureIndependenceTest {

  @Test
  void twoPlayersFromOneBuilder_doNotShareAScore() {
    PlayerBuilder builder = PlayerBuilder.newPlayer().named("Fighter");
    Player first = builder.build();
    Player second = builder.build();

    assertThat(first.getPoints())
        .as("score objects must be distinct")
        .isNotSameAs(second.getPoints());
    assertThat(first.getFouls()).as("foul objects must be distinct").isNotSameAs(second.getFouls());

    first.addPoint(PointsType.IPPON);
    first.addFoul();

    assertThat(second.getPoints().getNumOfPoints())
        .as("scoring against one fixture must not move another's score")
        .isZero();
    assertThat(second.getFouls().getNumOfFouls()).isZero();
  }

  @Test
  void mutatingTheBuilderAfterBuilding_doesNotChangeTheBuiltPlayer() {
    PlayerBuilder builder = PlayerBuilder.newPlayer();
    Player built = builder.build();

    builder.scoring(PointsType.IPPON).withFouls(2);

    assertThat(built.getPoints().getNumOfPoints())
        .as("a player already built is finished; the builder cannot reach back into it")
        .isZero();
    assertThat(built.getFouls().getNumOfFouls()).isZero();
  }

  @Test
  void requestedScoreAndFouls_stillReachTheBuiltPlayer() {
    Player player =
        PlayerBuilder.newPlayer()
            .scoring(PointsType.IPPON)
            .scoring(PointsType.YUKO, 2)
            .withFouls(3)
            .build();

    assertThat(player.getPoints().getNumOfPoints()).as("IPPON 3 + YUKO 1 + YUKO 1").isEqualTo(5);
    assertThat(player.getFouls().getNumOfFouls()).isEqualTo(3);
  }

  @Test
  void twoMatchesFromOneBuilder_doNotShareFighters() {
    KumiteGameBuilder builder = KumiteGameBuilder.newGame();
    KumiteGame first = builder.build();
    KumiteGame second = builder.build();

    Player firstRed = first.getPlayersMap().get(PlayerColor.RED);
    Player secondRed = second.getPlayersMap().get(PlayerColor.RED);

    assertThat(firstRed).isNotSameAs(secondRed);

    firstRed.addPoint(PointsType.IPPON);

    assertThat(secondRed.getPoints().getNumOfPoints())
        .as("two matches built from one builder are independent all the way down")
        .isZero();
  }

  @Test
  void aFighterSuppliedByTheCaller_isNotMutatedByTheMatch() {
    Player supplied = PlayerBuilder.newPlayer().named("Kenji").build();

    KumiteGame game = KumiteGameBuilder.newGame().with(PlayerColor.RED, supplied).build();
    game.getPlayersMap().get(PlayerColor.RED).addPoint(PointsType.IPPON);

    assertThat(supplied.getPoints().getNumOfPoints())
        .as("the caller's object is theirs; the match takes a copy")
        .isZero();
  }
}

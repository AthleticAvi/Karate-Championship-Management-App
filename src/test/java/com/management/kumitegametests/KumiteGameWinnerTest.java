package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.enums.PlayerColor;
import com.management.exceptions.PlayerNotFoundException;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.PlayerBuilder;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The winner field, after #34 turned it from a display sentence into a colour.
 *
 * <p>Unit level: none of this needs the container, a database or the web layer. The rendered form
 * of the winner on the wire is asserted in the slice tests instead.
 */
class KumiteGameWinnerTest {

  @Test
  void newGame_hasNoWinner() {
    KumiteGame game = KumiteGameBuilder.newGame().build();

    assertThat(game.getWinner())
        .as("absence is null, not the string \"Pending game ending\"")
        .isNull();
  }

  @Test
  void updateWinner_whenTheColourIsInTheGame_recordsThatColour() {
    KumiteGame game = KumiteGameBuilder.newGame().build();

    game.updateWinner(PlayerColor.BLUE);

    assertThat(game.getWinner()).isEqualTo(PlayerColor.BLUE);
  }

  @Test
  void updateWinner_whenTheColourIsNotInTheGame_throwsPlayerNotFound() {
    Map<PlayerColor, Player> onlyRed = new EnumMap<>(PlayerColor.class);
    onlyRed.put(PlayerColor.RED, PlayerBuilder.newPlayer().build());
    KumiteGame game = new KumiteGame(onlyRed, List.of(), Duration.ofSeconds(120));

    assertThatThrownBy(() -> game.updateWinner(PlayerColor.BLUE))
        .isInstanceOf(PlayerNotFoundException.class);
  }
}

package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.dto.KumiteGameRequest;
import com.management.dto.MatchFighterRequest;
import com.management.mappers.KumiteGameMapper;
import com.management.models.NewMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KumiteGameMapperTest {

  @Test
  void toNewMatch_carriesEveryFighterRefereeAndDuration() {
    KumiteGameRequest request =
        new KumiteGameRequest(
            Map.of(
                "first", new MatchFighterRequest("Kenji", "RED"),
                "second", new MatchFighterRequest("Sato", "BLUE")),
            List.of("Referee One"),
            180);

    NewMatch newMatch = KumiteGameMapper.toNewMatch(request);

    assertThat(newMatch.fighters())
        .containsExactlyInAnyOrder(
            new NewMatch.Fighter("Kenji", "RED"), new NewMatch.Fighter("Sato", "BLUE"));
    assertThat(newMatch.referees()).containsExactly("Referee One");
    assertThat(newMatch.gameDuration()).isEqualTo(180);
  }

  @Test
  void toNewMatch_keepsItsOwnCopyOfTheReferees() {
    List<String> referees = new ArrayList<>(List.of("Referee One"));
    KumiteGameRequest request =
        new KumiteGameRequest(
            Map.of("first", new MatchFighterRequest("Kenji", "RED")), referees, null);

    NewMatch newMatch = KumiteGameMapper.toNewMatch(request);
    referees.add("Referee Two");

    assertThat(newMatch.referees()).containsExactly("Referee One");
  }

  @Test
  void toNewMatch_withNoDuration_leavesItEmptyForTheDefault() {
    KumiteGameRequest request =
        new KumiteGameRequest(
            Map.of("first", new MatchFighterRequest("Kenji", "RED")), List.of("Referee One"), null);

    assertThat(KumiteGameMapper.toNewMatch(request).gameDuration()).isNull();
  }
}

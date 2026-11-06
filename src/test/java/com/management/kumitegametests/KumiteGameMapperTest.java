package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.dto.FighterRequest;
import com.management.dto.KumiteGameRequest;
import com.management.mappers.KumiteGameMapper;
import com.management.models.NewMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KumiteGameMapperTest {

  @Test
  void toNewMatch_carriesEveryFighterRefereeAndDuration() {
    KumiteGameRequest request =
        new KumiteGameRequest(
            Map.of(
                "first", new FighterRequest("Kenji", "RED"),
                "second", new FighterRequest("Sato", "BLUE")),
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
  void toNewMatch_withNoDuration_leavesItEmptyForTheDefault() {
    KumiteGameRequest request =
        new KumiteGameRequest(
            Map.of("first", new FighterRequest("Kenji", "RED")), List.of("Referee One"), null);

    assertThat(KumiteGameMapper.toNewMatch(request).gameDuration()).isNull();
  }
}

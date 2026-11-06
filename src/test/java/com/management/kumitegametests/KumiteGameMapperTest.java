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
            Map.of("any label", new FighterRequest("Kenji", "RED")), List.of("Referee One"), 180);

    NewMatch newMatch = KumiteGameMapper.toNewMatch(request);

    assertThat(newMatch.fighters()).containsExactly(new NewMatch.Fighter("Kenji", "RED"));
    assertThat(newMatch.referees()).containsExactly("Referee One");
    assertThat(newMatch.gameDuration()).isEqualTo(180);
  }
}

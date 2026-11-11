package com.management.kumitegame.service;

import com.management.athlete.service.AthleteReferences;
import com.management.kumitegame.data.KumiteGameRepository;
import com.management.referee.service.RefereeReferences;
import org.springframework.stereotype.Component;

/**
 * Tells the athlete and referee features whether a game refers to one of theirs, so deleting it can
 * be refused.
 *
 * <p>Here, because this feature may use those two and never the reverse: they own the question,
 * this answers it. Any assignment counts, a removed one included, so every game a read can show
 * still finds its names.
 */
@Component
public class GameReferences implements AthleteReferences, RefereeReferences {

  private final KumiteGameRepository kumiteGameRepository;

  public GameReferences(KumiteGameRepository kumiteGameRepository) {
    this.kumiteGameRepository = kumiteGameRepository;
  }

  @Override
  public boolean isAthleteReferred(String athleteId) {
    return kumiteGameRepository.existsByAssignmentsAthleteId(athleteId);
  }

  @Override
  public boolean isRefereeReferred(String refereeId) {
    return kumiteGameRepository.existsByAssignmentsRefereeId(refereeId);
  }
}

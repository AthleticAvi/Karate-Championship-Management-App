package com.management.kumitegame.data;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Games, one document each.
 *
 * <p>The two reference checks scan the collection: there is no index beyond {@code _id} until a
 * measured query needs one, and at one championship's load a scan is enough.
 */
public interface KumiteGameRepository extends MongoRepository<KumiteGameDocument, String> {

  /** Whether any game ever assigned this athlete, removed later or not. */
  boolean existsByAssignmentsAthleteId(String athleteId);

  /** Whether any game ever assigned this referee, removed later or not. */
  boolean existsByAssignmentsRefereeId(String refereeId);
}

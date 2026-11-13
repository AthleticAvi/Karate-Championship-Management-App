package com.management.athlete.service;

import com.management.athlete.data.AthleteDocument;
import com.management.athlete.data.AthleteDocumentMapper;
import com.management.athlete.data.AthleteRepository;
import com.management.athlete.domain.Athlete;
import com.management.athlete.domain.AthleteChangedException;
import com.management.athlete.domain.AthleteInUseException;
import com.management.athlete.domain.AthleteNotFoundException;
import com.management.service.IdentityService;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * The athlete aggregate: create and update here, and what every named identity shares in {@link
 * IdentityService}, each one document.
 *
 * <p>Whether a game refers to an athlete comes through {@link AthleteReferences}, which the game
 * feature implements.
 */
@Service
public class AthleteService extends IdentityService<Athlete, AthleteDocument> {

  private final AthleteReferences references;

  public AthleteService(AthleteRepository athleteRepository, AthleteReferences references) {
    super(athleteRepository);
    this.references = references;
  }

  public Athlete create(String name, @Nullable Double weightKg) {
    return insert(AthleteDocumentMapper.toNewDocument(name, weightKg));
  }

  /** Saves against the version that was read. Values already stored write nothing. */
  public Athlete update(String athleteId, String name, @Nullable Double weightKg) {
    AthleteDocument stored = stored(athleteId);
    if (stored.getName().equals(name) && Objects.equals(stored.getWeightKg(), weightKg)) {
      return toDomain(stored);
    }
    return save(AthleteDocumentMapper.toDocument(toDomain(stored).update(name, weightKg), stored));
  }

  @Override
  protected Athlete toDomain(AthleteDocument stored) {
    return AthleteDocumentMapper.toDomain(stored);
  }

  @Override
  protected boolean isReferred(String athleteId) {
    return references.isAthleteReferred(athleteId);
  }

  @Override
  protected AthleteNotFoundException notFound(String athleteId) {
    return new AthleteNotFoundException("No athlete has the id " + athleteId);
  }

  @Override
  protected AthleteInUseException inUse(String athleteId) {
    return new AthleteInUseException(
        "A game refers to athlete " + athleteId + ", so it cannot be deleted.");
  }

  @Override
  protected AthleteChangedException changed(Athlete current, Throwable cause) {
    return new AthleteChangedException(current, cause);
  }
}

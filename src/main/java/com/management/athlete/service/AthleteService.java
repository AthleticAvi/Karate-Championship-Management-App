package com.management.athlete.service;

import com.management.athlete.data.AthleteDocument;
import com.management.athlete.data.AthleteDocumentMapper;
import com.management.athlete.data.AthleteRepository;
import com.management.athlete.domain.Athlete;
import com.management.athlete.domain.AthleteChangedException;
import com.management.athlete.domain.AthleteInUseException;
import com.management.athlete.domain.AthleteNotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * The athlete aggregate: create, read, update and delete, each one document.
 *
 * <p>Knows nothing of games. Whether a game refers to an athlete comes through {@link
 * AthleteReferences}, which the game feature implements.
 *
 * <p><strong>An update is not retried.</strong> It sets values, so a retry would overwrite what the
 * caller never saw. An update that loses to another write answers with the athlete as it now
 * stands: see {@code [version-writes]} in {@code
 * context/workflow/design/concurrency-consistency.md}.
 */
@Service
public class AthleteService {

  private final AthleteRepository athleteRepository;
  private final AthleteReferences references;

  public AthleteService(AthleteRepository athleteRepository, AthleteReferences references) {
    this.athleteRepository = athleteRepository;
    this.references = references;
  }

  public Athlete create(String name, @Nullable Double weightKg) {
    return AthleteDocumentMapper.toDomain(
        athleteRepository.save(AthleteDocumentMapper.toNewDocument(name, weightKg)));
  }

  public Athlete get(String athleteId) {
    return AthleteDocumentMapper.toDomain(stored(athleteId));
  }

  /** The athletes with these ids that exist, by id. A missing one is simply absent. */
  public Map<String, Athlete> findAll(Collection<String> athleteIds) {
    return findAllVersioned(athleteIds).values().stream()
        .map(VersionedAthlete::athlete)
        .collect(Collectors.toMap(Athlete::id, Function.identity()));
  }

  /**
   * The athletes with these ids that exist, by id, each with the version it is stored at. The
   * athlete and its version come from one read, so a tag built from them never pairs an old name
   * with a newer version.
   */
  public Map<String, VersionedAthlete> findAllVersioned(Collection<String> athleteIds) {
    return athleteRepository.findAllById(athleteIds).stream()
        .collect(
            Collectors.toMap(
                AthleteDocument::getId,
                stored ->
                    new VersionedAthlete(
                        AthleteDocumentMapper.toDomain(stored), stored.getVersion())));
  }

  /**
   * Saves against the version that was read, so a write in between fails the save. An athlete
   * deleted in between answers 404 from the second read. Values already stored write nothing.
   */
  public Athlete update(String athleteId, String name, @Nullable Double weightKg) {
    AthleteDocument stored = stored(athleteId);
    if (stored.getName().equals(name) && Objects.equals(stored.getWeightKg(), weightKg)) {
      return AthleteDocumentMapper.toDomain(stored);
    }
    Athlete updated = AthleteDocumentMapper.toDomain(stored).update(name, weightKg);
    try {
      return AthleteDocumentMapper.toDomain(
          athleteRepository.save(AthleteDocumentMapper.toDocument(updated, stored)));
    } catch (OptimisticLockingFailureException lost) {
      throw new AthleteChangedException(get(athleteId), lost);
    }
  }

  /**
   * Deletes by id, refused while a game refers to the athlete. A game created between the check and
   * the delete is the accepted residual race: it reads the athlete as unknown.
   */
  public void delete(String athleteId) {
    if (references.isAthleteReferred(athleteId)) {
      throw new AthleteInUseException(
          "A game refers to athlete " + athleteId + ", so it cannot be deleted.");
    }
    if (athleteRepository.removeById(athleteId) == 0) {
      throw notFound(athleteId);
    }
  }

  private AthleteDocument stored(String athleteId) {
    return athleteRepository.findById(athleteId).orElseThrow(() -> notFound(athleteId));
  }

  private static AthleteNotFoundException notFound(String athleteId) {
    return new AthleteNotFoundException("No athlete has the id " + athleteId);
  }
}

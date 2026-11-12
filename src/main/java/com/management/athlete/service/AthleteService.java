package com.management.athlete.service;

import com.management.athlete.data.AthleteDocument;
import com.management.athlete.data.AthleteDocumentMapper;
import com.management.athlete.data.AthleteRepository;
import com.management.athlete.domain.Athlete;
import com.management.athlete.domain.AthleteChangedException;
import com.management.athlete.domain.AthleteInUseException;
import com.management.athlete.domain.AthleteNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

  /** By name, then by id, so two athletes with one name keep their place between pages. */
  private static final Sort BY_NAME = Sort.by("name", "id");

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

  /**
   * A page of the athletes whose name contains {@code name}, in any case, or of every athlete when
   * it is empty. Always in name order: the caller picks the page and its size, never the order.
   *
   * <p>A page that starts past what the datastore can skip, an int, is empty without asking it: the
   * skip would wrap and answer another page.
   */
  public Page<Athlete> list(String name, Pageable pageable) {
    PageRequest request = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_NAME);
    if (request.getOffset() > Integer.MAX_VALUE) {
      return new PageImpl<>(
          List.of(), request, athleteRepository.countByNameContainingIgnoreCase(name));
    }
    return athleteRepository
        .findByNameContainingIgnoreCase(name, request)
        .map(AthleteDocumentMapper::toDomain);
  }

  /** The athletes with these ids that exist, by id. A missing one is simply absent. */
  public Map<String, Athlete> findAll(Collection<String> athleteIds) {
    return findAllVersioned(athleteIds).entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, found -> found.getValue().athlete()));
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

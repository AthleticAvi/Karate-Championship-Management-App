package com.management.referee.service;

import com.management.referee.data.RefereeDocument;
import com.management.referee.data.RefereeDocumentMapper;
import com.management.referee.data.RefereeRepository;
import com.management.referee.domain.Referee;
import com.management.referee.domain.RefereeChangedException;
import com.management.referee.domain.RefereeInUseException;
import com.management.referee.domain.RefereeNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * The referee aggregate: create, read, rename and delete, each one document.
 *
 * <p>Knows nothing of games. Whether a game refers to a referee comes through {@link
 * RefereeReferences}, which the game feature implements.
 *
 * <p><strong>A rename is not retried.</strong> It sets a value, so a retry would overwrite a name
 * the caller never saw. A rename that loses to another write answers with the referee as it now
 * stands: see {@code [version-writes]} in {@code
 * context/workflow/design/concurrency-consistency.md}.
 */
@Service
public class RefereeService {

  /** By name, then by id, so two referees with one name keep their place between pages. */
  private static final Sort BY_NAME = Sort.by("name", "id");

  private final RefereeRepository refereeRepository;
  private final RefereeReferences references;

  public RefereeService(RefereeRepository refereeRepository, RefereeReferences references) {
    this.refereeRepository = refereeRepository;
    this.references = references;
  }

  public Referee create(String name) {
    return RefereeDocumentMapper.toDomain(
        refereeRepository.save(RefereeDocumentMapper.toNewDocument(name)));
  }

  public Referee get(String refereeId) {
    return RefereeDocumentMapper.toDomain(stored(refereeId));
  }

  /**
   * A page of the referees whose name contains {@code name}, in any case, or of every referee when
   * it is empty. Always in name order: the caller picks the page and its size, never the order.
   *
   * <p>A page that starts past what the datastore can skip, an int, is empty without asking it: the
   * skip would wrap and answer another page.
   */
  public Page<Referee> list(String name, Pageable pageable) {
    PageRequest request = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_NAME);
    if (request.getOffset() > Integer.MAX_VALUE) {
      return new PageImpl<>(
          List.of(), request, refereeRepository.countByNameContainingIgnoreCase(name));
    }
    return refereeRepository
        .findByNameContainingIgnoreCase(name, request)
        .map(RefereeDocumentMapper::toDomain);
  }

  /** The referees with these ids that exist, by id. A missing one is simply absent. */
  public Map<String, Referee> findAll(Collection<String> refereeIds) {
    return findAllVersioned(refereeIds).entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, found -> found.getValue().referee()));
  }

  /**
   * The referees with these ids that exist, by id, each with the version it is stored at. The
   * referee and its version come from one read, so a tag built from them never pairs an old name
   * with a newer version.
   */
  public Map<String, VersionedReferee> findAllVersioned(Collection<String> refereeIds) {
    return refereeRepository.findAllById(refereeIds).stream()
        .collect(
            Collectors.toMap(
                RefereeDocument::getId,
                stored ->
                    new VersionedReferee(
                        RefereeDocumentMapper.toDomain(stored), stored.getVersion())));
  }

  /**
   * Saves against the version that was read, so a write in between fails the save. A referee
   * deleted in between answers 404 from the second read. The name already stored writes nothing.
   */
  public Referee rename(String refereeId, String name) {
    RefereeDocument stored = stored(refereeId);
    if (stored.getName().equals(name)) {
      return RefereeDocumentMapper.toDomain(stored);
    }
    Referee renamed = RefereeDocumentMapper.toDomain(stored).rename(name);
    try {
      return RefereeDocumentMapper.toDomain(
          refereeRepository.save(RefereeDocumentMapper.toDocument(renamed, stored)));
    } catch (OptimisticLockingFailureException lost) {
      throw new RefereeChangedException(get(refereeId), lost);
    }
  }

  /**
   * Deletes by id, refused while a game refers to the referee. No version check: a delete does not
   * depend on the name it removes. A game created between the check and the delete is the accepted
   * residual race: it reads the referee as unknown.
   */
  public void delete(String refereeId) {
    if (references.isRefereeReferred(refereeId)) {
      throw new RefereeInUseException(
          "A game refers to referee " + refereeId + ", so it cannot be deleted.");
    }
    if (refereeRepository.removeById(refereeId) == 0) {
      throw notFound(refereeId);
    }
  }

  private RefereeDocument stored(String refereeId) {
    return refereeRepository.findById(refereeId).orElseThrow(() -> notFound(refereeId));
  }

  private static RefereeNotFoundException notFound(String refereeId) {
    return new RefereeNotFoundException("No referee has the id " + refereeId);
  }
}

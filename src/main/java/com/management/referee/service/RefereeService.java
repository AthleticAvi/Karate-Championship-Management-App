package com.management.referee.service;

import com.management.referee.data.RefereeDocument;
import com.management.referee.data.RefereeDocumentMapper;
import com.management.referee.data.RefereeRepository;
import com.management.referee.domain.Referee;
import com.management.referee.domain.RefereeChangedException;
import com.management.referee.domain.RefereeNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * The referee aggregate: create, read, rename and delete, each one document.
 *
 * <p>A leaf: it knows nothing of matches. From #155 a match refers to referees by id, and deleting
 * one a match refers to is refused there.
 *
 * <p><strong>A rename is not retried.</strong> It sets a value, so a retry would overwrite a name
 * the caller never saw. A rename that loses to another write answers with the referee as it now
 * stands: see {@code [version-writes]} in {@code
 * context/workflow/design/concurrency-consistency.md}.
 */
@Service
public class RefereeService {

  private final RefereeRepository refereeRepository;

  public RefereeService(RefereeRepository refereeRepository) {
    this.refereeRepository = refereeRepository;
  }

  public Referee create(String name) {
    return RefereeDocumentMapper.toDomain(
        refereeRepository.save(RefereeDocumentMapper.toNewDocument(name)));
  }

  public Referee get(String refereeId) {
    return RefereeDocumentMapper.toDomain(stored(refereeId));
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

  /** Deletes by id, with no version check: a delete does not depend on the name it removes. */
  public void delete(String refereeId) {
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

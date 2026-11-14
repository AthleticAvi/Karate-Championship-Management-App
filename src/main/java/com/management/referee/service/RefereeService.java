package com.management.referee.service;

import com.management.referee.data.RefereeDocument;
import com.management.referee.data.RefereeDocumentMapper;
import com.management.referee.data.RefereeRepository;
import com.management.referee.domain.Referee;
import com.management.referee.domain.RefereeChangedException;
import com.management.referee.domain.RefereeInUseException;
import com.management.referee.domain.RefereeNotFoundException;
import com.management.service.IdentityService;
import org.springframework.stereotype.Service;

/**
 * The referee aggregate: create and rename here, and what every named identity shares in {@link
 * IdentityService}, each one document.
 *
 * <p>Whether a game refers to a referee comes through {@link RefereeReferences}, which the game
 * feature implements.
 */
@Service
public class RefereeService extends IdentityService<Referee, RefereeDocument> {

  private final RefereeReferences references;

  public RefereeService(RefereeRepository refereeRepository, RefereeReferences references) {
    super(refereeRepository);
    this.references = references;
  }

  public Referee create(String name) {
    return insert(RefereeDocumentMapper.toNewDocument(name));
  }

  /** Saves against the version that was read. The name already stored writes nothing. */
  public Referee rename(String refereeId, String name) {
    RefereeDocument stored = stored(refereeId);
    if (stored.getName().equals(name)) {
      return toDomain(stored);
    }
    return save(RefereeDocumentMapper.toDocument(toDomain(stored).rename(name), stored));
  }

  @Override
  protected Referee toDomain(RefereeDocument stored) {
    return RefereeDocumentMapper.toDomain(stored);
  }

  @Override
  protected boolean isReferred(String refereeId) {
    return references.isRefereeReferred(refereeId);
  }

  @Override
  protected RefereeNotFoundException notFound(String refereeId) {
    return new RefereeNotFoundException("No referee has the id " + refereeId);
  }

  @Override
  protected RefereeInUseException inUse(String refereeId) {
    return new RefereeInUseException(
        "A game refers to referee " + refereeId + ", so it cannot be deleted.");
  }

  @Override
  protected RefereeChangedException changed(Referee current, Throwable cause) {
    return new RefereeChangedException(current, cause);
  }
}

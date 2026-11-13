package com.management.service;

import com.management.data.IdentityDocument;
import com.management.data.IdentityRepository;
import com.management.domain.IdentityChangedException;
import com.management.domain.IdentityInUseException;
import com.management.domain.IdentityNotFoundException;
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

/**
 * A named identity kept once and outside any game, such as an athlete or a referee: the reads, the
 * list, the delete and the checked save that every one of them shares, each one document. A
 * feature's service extends it with its own create and change, which differ by its fields.
 *
 * <p>Knows nothing of games. Whether a game refers to one comes through {@link #isReferred}, which
 * each feature answers through an interface it owns and the game feature implements.
 *
 * <p><strong>A change is not retried.</strong> It sets values, so a retry would overwrite what the
 * caller never saw. A change that loses to another write answers with the identity as it now
 * stands: see {@code [version-writes]} in {@code
 * context/workflow/design/concurrency-consistency.md}.
 *
 * @param <T> the domain type
 * @param <D> its stored document
 */
public abstract class IdentityService<T, D extends IdentityDocument> {

  /** By name, then by id, so two with one name keep their place between pages. */
  private static final Sort BY_NAME = Sort.by("name", "id");

  private final IdentityRepository<D> repository;

  protected IdentityService(IdentityRepository<D> repository) {
    this.repository = repository;
  }

  public T get(String id) {
    return toDomain(stored(id));
  }

  /**
   * A page of those whose name contains {@code name}, in any case, or of every one when it is
   * empty. Always in name order: the caller picks the page and its size, never the order.
   *
   * <p>A page that starts past what the datastore can skip, an int, is empty without asking it: the
   * skip would wrap and answer another page.
   */
  public Page<T> list(String name, Pageable pageable) {
    PageRequest request = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_NAME);
    if (request.getOffset() > Integer.MAX_VALUE) {
      return new PageImpl<>(List.of(), request, repository.countByNameContainingIgnoreCase(name));
    }
    return repository.findByNameContainingIgnoreCase(name, request).map(this::toDomain);
  }

  /** Those with these ids that exist, by id. A missing one is simply absent. */
  public Map<String, T> findAll(Collection<String> ids) {
    return repository.findAllById(ids).stream()
        .collect(Collectors.toMap(IdentityDocument::getId, this::toDomain));
  }

  /**
   * Those with these ids that exist, by id, each with the version it is stored at. Each and its
   * version come from one read, so a tag built from them never pairs an old name with a newer
   * version.
   */
  public Map<String, Versioned<T>> findAllVersioned(Collection<String> ids) {
    return repository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                IdentityDocument::getId,
                stored -> new Versioned<>(toDomain(stored), stored.getVersion())));
  }

  /**
   * Deletes by id, refused while a game refers to it. No version check: a delete does not depend on
   * the values it removes. A game created between the check and the delete is the accepted residual
   * race: it reads the identity as unknown.
   */
  public void delete(String id) {
    if (isReferred(id)) {
      throw inUse(id);
    }
    if (repository.removeById(id) == 0) {
      throw notFound(id);
    }
  }

  /** Stores a new one. The insert assigns the id and the version, and auditing the instants. */
  protected T insert(D created) {
    return toDomain(repository.save(created));
  }

  /**
   * Saves a change against the version that was read, so a write in between fails the save. One
   * deleted in between answers not found from the second read.
   */
  protected T save(D changed) {
    try {
      return toDomain(repository.save(changed));
    } catch (OptimisticLockingFailureException lost) {
      throw changed(get(changed.getId()), lost);
    }
  }

  protected D stored(String id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  protected abstract T toDomain(D stored);

  /** Whether a game refers to it, so deleting it would leave a dangling id. */
  protected abstract boolean isReferred(String id);

  protected abstract IdentityNotFoundException notFound(String id);

  protected abstract IdentityInUseException inUse(String id);

  protected abstract IdentityChangedException changed(T current, Throwable cause);
}

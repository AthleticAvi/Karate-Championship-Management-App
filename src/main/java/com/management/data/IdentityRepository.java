package com.management.data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Stored named identities of one kind, one document each. Each feature's repository extends it,
 * which is what makes Spring Data create one for that feature's collection.
 *
 * <p>The name search scans the collection: a match anywhere in the name, in any case, cannot use an
 * index, and at one championship's load a scan is enough.
 */
@NoRepositoryBean
public interface IdentityRepository<D extends IdentityDocument> extends MongoRepository<D, String> {

  /** Deletes by id with no version check, in one call. Returns how many were deleted: 0 or 1. */
  long removeById(String id);

  /**
   * Those whose name contains this text, in any case; every one when it is empty. The text is
   * matched as it is written, never as a pattern.
   */
  Page<D> findByNameContainingIgnoreCase(String name, Pageable pageable);

  /** How many {@link #findByNameContainingIgnoreCase} finds in all. */
  long countByNameContainingIgnoreCase(String name);
}

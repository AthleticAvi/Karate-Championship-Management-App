package com.management.athlete.data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Athletes, one document each.
 *
 * <p>The name search scans the collection: a match anywhere in the name, in any case, cannot use an
 * index, and at one championship's load a scan is enough.
 */
public interface AthleteRepository extends MongoRepository<AthleteDocument, String> {

  /** Deletes by id with no version check, in one call. Returns how many were deleted: 0 or 1. */
  long removeById(String id);

  /**
   * The athletes whose name contains this text, in any case; every athlete when it is empty. The
   * text is matched as it is written, never as a pattern.
   */
  Page<AthleteDocument> findByNameContainingIgnoreCase(String name, Pageable pageable);

  /** How many athletes {@link #findByNameContainingIgnoreCase} finds in all. */
  long countByNameContainingIgnoreCase(String name);
}

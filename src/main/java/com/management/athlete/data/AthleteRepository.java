package com.management.athlete.data;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AthleteRepository extends MongoRepository<AthleteDocument, String> {

  /** Deletes by id with no version check, in one call. Returns how many were deleted: 0 or 1. */
  long removeById(String id);
}

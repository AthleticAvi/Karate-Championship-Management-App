package com.management.referee.data;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RefereeRepository extends MongoRepository<RefereeDocument, String> {}

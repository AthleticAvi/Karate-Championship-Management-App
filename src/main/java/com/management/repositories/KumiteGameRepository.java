package com.management.repositories;

import com.management.models.KumiteGame;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface KumiteGameRepository extends MongoRepository<KumiteGame, String> {

}

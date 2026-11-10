package com.management.kumitegame.data;

import com.management.kumitegame.domain.KumiteGame;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface KumiteGameRepository extends MongoRepository<KumiteGame, String> {}

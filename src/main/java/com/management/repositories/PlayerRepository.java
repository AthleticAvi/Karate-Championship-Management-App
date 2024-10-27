package com.management.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.management.models.Player;

public interface PlayerRepository extends MongoRepository<Player, String> {

}

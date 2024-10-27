package com.management.kumitegame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

//@RestController
//@RequestMapping("/api")
@SpringBootApplication(scanBasePackages = "com.management")
@EnableMongoRepositories(basePackages = "com.management.repositories")
public class KumiteGameController {
    private static final Logger logger = LoggerFactory.getLogger(KumiteGameController.class);
    public static void main(String[] args) {
        logger.info("Management - Kumite Management - Main - Started the application");
        SpringApplication.run(KumiteGameController.class, args);
    }
}

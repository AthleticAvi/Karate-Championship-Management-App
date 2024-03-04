package com.management.kumitmanagement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KarateChampionshipApplication {
    private static final Logger logger = LoggerFactory.getLogger(KarateChampionshipApplication.class);

    public static void main(String[] args) {
        logger.info("Management - Kumite Management - Main - Started the application");
        SpringApplication.run(KarateChampionshipApplication.class, args);
    }
}

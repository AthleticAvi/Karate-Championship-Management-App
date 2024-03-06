package com.management.kumitmanagement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class KarateChampionshipApplication {
    private static final Logger logger = LoggerFactory.getLogger(KarateChampionshipApplication.class);

    public static void main(String[] args) {
        logger.info("Management - Kumite Management - Main - Started the application");
        SpringApplication.run(KarateChampionshipApplication.class, args);
    }

    @GetMapping
    public String starterMessage(){
        return "Welcome to the Karate Championship Management App";
    }
}

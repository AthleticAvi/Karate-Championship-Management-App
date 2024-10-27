package com.management.kumitegame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@RequestMapping("/api")
public class KumiteGameController {
    private static final Logger logger = LoggerFactory.getLogger(KumiteGameController.class);

    public static void main(String[] args) {
        logger.info("Management - Kumite Management - Main - Started the application");
        SpringApplication.run(KumiteGameController.class, args);
    }

    @GetMapping("/starterMessage")
    public String starterMessage(){
        return "Welcome to the Karate Championship Management App";
    }
}

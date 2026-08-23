package com.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * The application entry point, at the root of the package tree — deliberately.
 *
 * <p>Component scanning, repository discovery and the configuration anchor the test slices search
 * for all derive from this class's package. When it lived in {@code com.management.kumitegame},
 * every one of those had to be spelled out by hand: {@code scanBasePackages},
 * {@code @EnableMongoRepositories(basePackages = ...)}, and a separate empty {@code
 * SliceTestConfiguration} for the tests to find. Sitting here, all three defaults are simply
 * correct.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableResilientMethods
public class KumiteGameStarter {

  public static void main(String[] args) {
    SpringApplication.run(KumiteGameStarter.class, args);
  }
}

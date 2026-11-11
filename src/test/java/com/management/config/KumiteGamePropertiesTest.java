package com.management.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.kumitegame.domain.Side;
import com.management.testsupport.TestGames;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * The rule values bind from the real {@code application.properties}, and a bad one stops the
 * application before anything runs.
 */
class KumiteGamePropertiesTest {

  @EnableConfigurationProperties(KumiteGameProperties.class)
  static class Binding {}

  /** The {@code kumite-game.*} lines of the shipped file, as {@code key=value}. */
  private static List<String> shipped() throws IOException {
    Properties file =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
    List<String> values = new ArrayList<>();
    file.stringPropertyNames().stream()
        .filter(key -> key.startsWith("kumite-game."))
        .forEach(key -> values.add(key + "=" + file.getProperty(key)));
    return values;
  }

  private static ApplicationContextRunner runner(List<String> values) {
    return new ApplicationContextRunner()
        .withUserConfiguration(Binding.class)
        .withPropertyValues(values.toArray(String[]::new));
  }

  @Test
  void shippedValues_bindToTheWkfDefaults() throws IOException {
    runner(shipped())
        .run(context -> {
          assertThat(context).hasNotFailed();
          KumiteGameProperties properties = context.getBean(KumiteGameProperties.class);
          assertThat(properties.rules().snapshot()).isEqualTo(TestGames.settings());
          assertThat(properties.sideColours()).containsEntry(Side.AKA, "red");
          assertThat(properties.sideColours()).containsEntry(Side.AO, "blue");
        });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "kumite-game.rules.duration=0s",
        "kumite-game.rules.duration=-1s",
        "kumite-game.rules.medical-time=0s",
        "kumite-game.rules.lead-to-win=0",
        "kumite-game.rules.chui-limit=0",
        "kumite-game.rules.point-values.yuko=0",
        "kumite-game.rules.time-shown-tolerance=-1ms",
        "kumite-game.rules.durations-offered=",
        "kumite-game.rules.rulebook-version=",
        "kumite-game.side-colours.ao="
      })
  void badValue_stopsTheApplication(String bad) throws IOException {
    List<String> values = shipped();
    values.add(bad);

    runner(values).run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "kumite-game.rules.point-values.waza-ari",
        "kumite-game.side-colours.aka",
        "kumite-game.rules.duration"
      })
  void missingValue_stopsTheApplication(String missing) throws IOException {
    List<String> values = shipped();
    values.removeIf(value -> value.startsWith(missing + "="));

    runner(values).run(context -> assertThat(context).hasFailed());
  }
}

package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.KumiteGameStarter;
import com.management.config.StartupSettingsCheck;
import com.management.testsupport.IntegrationTestBase;
import com.mongodb.ConnectionString;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.w3c.dom.NodeList;

/**
 * Which profile the application starts under decides whether it starts at all.
 *
 * <p>Starts the application by hand: a failed start is the expected result here. The environment
 * variables are dropped, so a {@code MONGO_URI} on a developer's machine cannot change the result.
 */
class ProfileStartupIT {

  @Test
  void main_withNoProfile_refusesAndSaysWhatToSet() {
    assertThatThrownBy(() -> KumiteGameStarter.main(new String[] {"--server.port=0"}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No profile is set")
        .hasMessageContaining("SPRING_PROFILES_ACTIVE");
  }

  @Test
  void startup_underProdWithoutMongoUri_failsNamingIt() {
    assertThatThrownBy(() -> start("prod", "--server.port=0"))
        .hasMessageContaining("Could not resolve placeholder 'MONGO_URI'");
  }

  @Test
  void startup_underProfileWithNoFile_refuses() {
    assertThatThrownBy(() -> start("prd", "--server.port=0"))
        .hasMessageContaining("spring.mongodb.uri");
  }

  @Test
  void startup_underProdWithMongoUri_usesIt() {
    String uri = IntegrationTestBase.replicaSetUri();

    try (ConfigurableApplicationContext context =
        start("prod", "--server.port=0", "--MONGO_URI=" + uri)) {
      assertThat(connectionOf(context).getConnectionString()).isEqualTo(uri);
    }
  }

  /** Nothing listens in CI, so this proves the settings and not a connection. */
  @Test
  void startup_underDevWithNothingSet_targetsTheLocalReplicaSet() {
    try (ConfigurableApplicationContext context =
        start("dev", "--server.port=0", "--datastore.startup-wait=100ms")) {
      ConnectionString connection = connectionOf(context);

      assertThat(connection.getHosts()).containsExactly("localhost:27017");
      assertThat(connection.getRequiredReplicaSetName()).isEqualTo("rs0");
    }
  }

  @Test
  void mavenRun_startsUnderDev() throws Exception {
    NodeList profile =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(Path.of("pom.xml").toFile())
            .getElementsByTagName("spring-boot.run.profiles");

    assertThat(profile.getLength()).isEqualTo(1);
    assertThat(profile.item(0).getTextContent()).isEqualTo("dev");
  }

  private static ConfigurableApplicationContext start(String profile, String... args) {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    return new SpringApplicationBuilder(KumiteGameStarter.class)
        .environment(environment)
        .initializers(new StartupSettingsCheck())
        .profiles(profile)
        .run(args);
  }

  private static ConnectionString connectionOf(ConfigurableApplicationContext context) {
    return context.getBean(MongoConnectionDetails.class).getConnectionString();
  }
}

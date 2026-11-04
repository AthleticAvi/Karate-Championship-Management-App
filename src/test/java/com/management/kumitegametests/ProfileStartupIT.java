package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.management.KumiteGameStarter;
import com.management.config.StartupSettingsCheck;
import com.management.testsupport.IntegrationTestBase;
import com.mongodb.ConnectionString;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.w3c.dom.NodeList;

/**
 * Which profile the application starts under decides whether it starts at all.
 *
 * <p>Starts the application by hand: a failed start is the expected result here. The environment
 * variables are dropped, so a {@code MONGO_URI} on a developer's machine cannot change the result.
 * The one test of {@code main} cannot drop them, so it does not run with a profile already set.
 */
class ProfileStartupIT {

  @Test
  void main_withNoProfile_refusesAndSaysWhatToSet() {
    assumeThat(System.getenv("SPRING_PROFILES_ACTIVE")).isNull();

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
        .hasMessageContaining("prd")
        .hasMessageContaining("no datastore address");
  }

  @Test
  void startup_underProdWithEmptyMongoUri_failsNamingIt() {
    assertThatThrownBy(() -> start("prod", "--server.port=0", "--MONGO_URI="))
        .hasMessageContaining("MONGO_URI is empty");
  }

  @Test
  void startup_underProdWithMongoUri_usesIt() {
    String uri = IntegrationTestBase.replicaSetUri();

    try (ConfigurableApplicationContext context =
        start("prod", "--server.port=0", "--MONGO_URI=" + uri)) {
      assertThat(context.getBean(MongoConnectionDetails.class).getConnectionString())
          .hasToString(uri);
    }
  }

  /** No beans, so nothing connects: a database on this machine cannot change the result. */
  @Test
  void dev_withNothingSet_targetsTheLocalReplicaSet() {
    try (ConfigurableApplicationContext context =
        application(NoBeans.class, "dev").web(WebApplicationType.NONE).run()) {
      ConnectionString connection =
          new ConnectionString(context.getEnvironment().getRequiredProperty("spring.mongodb.uri"));

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
    return application(KumiteGameStarter.class, profile).run(args);
  }

  private static SpringApplicationBuilder application(Class<?> source, String profile) {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    return new SpringApplicationBuilder(source)
        .environment(environment)
        .initializers(new StartupSettingsCheck())
        .profiles(profile);
  }

  @Configuration(proxyBeanMethods = false)
  static class NoBeans {}
}

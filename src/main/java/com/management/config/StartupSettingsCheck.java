package com.management.config;

import java.util.Arrays;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Refuses to start without a profile and a datastore address, so a deployment never falls back to
 * local settings.
 *
 * <p>The framework stops neither case. With no profile it activates one named {@code default} and
 * carries on. A placeholder with no value is left as text by the binder, so the driver reports an
 * invalid connection string that names no setting. Reading the key here resolves it strictly, so
 * the failure names {@code MONGO_URI}. A blank value gets the same treatment. A profile with no
 * file of its own, such as a mistyped one, has no address at all, and without this would get the
 * driver's localhost default.
 *
 * <p>An initializer because it runs before any bean, so nothing reaches for the datastore first,
 * and after logging starts, so the message is logged normally. This is not branching on a profile:
 * it asks only whether one was chosen.
 */
public class StartupSettingsCheck
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    ConfigurableEnvironment environment = context.getEnvironment();
    if (environment.getActiveProfiles().length == 0) {
      throw new IllegalStateException(
          "No profile is set. Set SPRING_PROFILES_ACTIVE to prod on a deployment, or to dev on a"
              + " developer machine. mvn spring-boot:run already uses dev.");
    }
    String uri = environment.getProperty("spring.mongodb.uri");
    if (uri == null) {
      throw new IllegalStateException(
          "Profile "
              + Arrays.toString(environment.getActiveProfiles())
              + " has no datastore address. Check the profile name: use dev or prod.");
    }
    if (!StringUtils.hasText(uri)) {
      throw new IllegalStateException("MONGO_URI is empty. Set it to the connection string.");
    }
  }
}

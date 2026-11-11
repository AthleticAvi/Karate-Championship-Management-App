package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.management.account.data.AccountRepository;
import com.management.account.domain.Account;
import com.management.account.domain.AccountRole;
import com.management.testsupport.IntegrationTestBase;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.testcontainers.containers.GenericContainer;

/**
 * Where the first Admin comes from, and what a start with none does.
 *
 * <p>Starts the application by hand, each time on a database of its own: a refused start is the
 * expected result, and the rule depends on what the database holds.
 *
 * <p>Logging restarts with each application, so these read the output and not log events.
 */
@ExtendWith(OutputCaptureExtension.class)
class FirstAdminIT {

  private static final String USERNAME_KEY = "accounts.first-admin.username";
  private static final String PASSWORD_KEY = "accounts.first-admin.password";
  private static final String ADMIN = "first-admin";
  private static final String PASSWORD = UUID.randomUUID().toString();
  private static final String ATHLETES = "/api/v1/athletes";

  private final String database = "first-admin-" + UUID.randomUUID();

  private ConfigurableApplicationContext boot(String profile, String username, String password) {
    return boot(profile, IntegrationTestBase.replicaSetUri(), username, password, Map.of());
  }

  private ConfigurableApplicationContext boot(
      String profile,
      String uri,
      String username,
      String password,
      Map<String, String> otherSettings) {
    Map<String, String> settings = new HashMap<>(otherSettings);
    settings.put(USERNAME_KEY, username);
    settings.put(PASSWORD_KEY, password);
    settings.put("spring.mongodb.database", database);
    return IntegrationTestBase.startApplication(profile, uri, settings);
  }

  /** Writes first, through an application that needs no Admin, so the next start finds this. */
  private void store(Account account) {
    try (ConfigurableApplicationContext context = boot("dev", "", "")) {
      context.getBean(MongoTemplate.class).insert(account);
    }
  }

  private static int creationStatus(
      ConfigurableApplicationContext context, String username, String password) throws Exception {
    return IntegrationTestBase.send(
            IntegrationTestBase.request(context, ATHLETES)
                .header("Authorization", IntegrationTestBase.basic(username, password))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"name\":\"Kenji\"}")))
        .statusCode();
  }

  @Test
  void prod_withNoAdminAndNoValues_refusesNamingTheKeys() {
    assertThatThrownBy(() -> boot("prod", "", ""))
        .hasStackTraceContaining("ADMIN_USERNAME")
        .hasStackTraceContaining("ADMIN_PASSWORD");
  }

  @Test
  void prod_withOnlyOneValue_refuses() {
    assertThatThrownBy(() -> boot("prod", ADMIN, "")).hasStackTraceContaining("ADMIN_PASSWORD");
    assertThatThrownBy(() -> boot("prod", "", PASSWORD)).hasStackTraceContaining("ADMIN_USERNAME");
  }

  @Test
  void dev_withNoAdminAndNoValues_startsWithWarning(CapturedOutput output) {
    try (ConfigurableApplicationContext context = boot("dev", "", "")) {
      assertThat(context.isActive()).isTrue();
      assertThat(context.getBean(AccountRepository.class).count()).isZero();
    }

    assertThat(output.getAll().lines().filter(line -> line.contains("ADMIN_USERNAME")))
        .isNotEmpty()
        .allSatisfy(line -> assertThat(line).contains("WARN").contains("ADMIN_PASSWORD"));
  }

  @Test
  void prod_withValues_createsAdminWhoCanSignIn(CapturedOutput output) throws Exception {
    try (ConfigurableApplicationContext context = boot("prod", ADMIN, PASSWORD)) {
      Account stored = context.getBean(AccountRepository.class).findById(ADMIN).orElseThrow();

      assertThat(stored.getRole()).isEqualTo(AccountRole.ADMIN);
      assertThat(stored.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
      assertThat(creationStatus(context, ADMIN, PASSWORD)).isEqualTo(201);
    }

    assertThat(output.getAll()).contains("Created the first Admin").doesNotContain(PASSWORD);
  }

  /** Stored as the document id, which is what keeps a username unique on every instance. */
  @Test
  void account_isStoredWithTheUsernameAsItsId() {
    try (ConfigurableApplicationContext context = boot("prod", ADMIN, PASSWORD)) {
      MongoTemplate template = context.getBean(MongoTemplate.class);

      assertThat(template.getCollection("account").countDocuments()).isEqualTo(1);
      assertThat(template.getCollection("account").find().first().getString("_id"))
          .isEqualTo(ADMIN);
    }
  }

  @Test
  void valuesAreIgnored_onceAnAdminExists() throws Exception {
    try (ConfigurableApplicationContext first = boot("prod", ADMIN, PASSWORD)) {
      assertThat(first.isActive()).isTrue();
    }

    try (ConfigurableApplicationContext second = boot("prod", "another-admin", "another")) {
      assertThat(second.getBean(AccountRepository.class).findAll())
          .singleElement()
          .satisfies(account -> assertThat(account.getUsername()).isEqualTo(ADMIN));
      assertThat(creationStatus(second, ADMIN, PASSWORD)).isEqualTo(201);
      assertThat(creationStatus(second, "another-admin", "another")).isEqualTo(401);
    }
  }

  @Test
  void prod_withAnAdminAndNoValues_starts() {
    store(new Account(ADMIN, "{noop}unused", AccountRole.ADMIN));

    try (ConfigurableApplicationContext context = boot("prod", "", "")) {
      assertThat(context.isActive()).isTrue();
    }
  }

  @Test
  void usernameHeldByScorekeeper_refusesRatherThanStartingWithNoAdmin() {
    store(new Account(ADMIN, "{noop}unused", AccountRole.SCOREKEEPER));

    assertThatThrownBy(() -> boot("prod", ADMIN, PASSWORD))
        .hasStackTraceContaining("ADMIN_USERNAME already names an account");
  }

  @Test
  void passwordTooLongToHash_refusesNamingTheKey() {
    assertThatThrownBy(() -> boot("prod", ADMIN, "x".repeat(100)))
        .hasStackTraceContaining("ADMIN_PASSWORD");
  }

  /** A colon ends the username in Basic credentials, so this account could never sign in. */
  @Test
  void usernameThatCouldNeverSignIn_refusesNamingTheKey() {
    assertThatThrownBy(() -> boot("prod", "first:admin", PASSWORD))
        .hasStackTraceContaining("accounts.first-admin")
        .satisfies(failure -> assertThat(failure.toString()).doesNotContain(PASSWORD));
  }

  /** Accounts come from the datastore, so the framework has no generated login to announce. */
  @Test
  void noGeneratedPassword_isOfferedOrLogged(CapturedOutput output) {
    try (ConfigurableApplicationContext context = boot("dev", "", "")) {
      assertThat(context.getBeansOfType(UserDetailsService.class)).hasSize(1);
      assertThat(context.containsBean("inMemoryUserDetailsManager")).isFalse();
    }

    assertThat(output.getAll())
        .contains("Started")
        .doesNotContainIgnoringCase("generated security password");
  }

  /** Pausing keeps the port open and silent, so the first answer comes after startup. */
  @Test
  void datastoreFirstAnsweringAfterStartup_getsTheSameCheck() {
    GenericContainer<?> late = IntegrationTestBase.replicaSet();
    String uri =
        "mongodb://%s:%d/?directConnection=true"
            .formatted(late.getHost(), late.getFirstMappedPort());
    Map<String, String> quickStart = Map.of("datastore.startup-wait", "500ms");
    try {
      late.getDockerClient().pauseContainerCmd(late.getContainerId()).exec();

      try (ConfigurableApplicationContext refused = boot("prod", uri, "", "", quickStart)) {
        assertThat(refused.isActive()).as("an outage at startup is not a refusal").isTrue();

        late.getDockerClient().unpauseContainerCmd(late.getContainerId()).exec();

        await().atMost(Duration.ofSeconds(30)).until(() -> !refused.isActive());
      }

      late.getDockerClient().pauseContainerCmd(late.getContainerId()).exec();

      try (ConfigurableApplicationContext accepted =
          boot("prod", uri, ADMIN, PASSWORD, quickStart)) {
        late.getDockerClient().unpauseContainerCmd(late.getContainerId()).exec();

        await()
            .atMost(Duration.ofSeconds(30))
            .until(() -> accepted.getBean(AccountRepository.class).existsById(ADMIN));
      }
    } finally {
      late.stop();
    }
  }
}

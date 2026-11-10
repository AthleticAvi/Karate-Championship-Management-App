package com.management.testsupport;

import static org.awaitility.Awaitility.await;

import com.management.KumiteGameStarter;
import com.management.account.domain.Account;
import com.management.account.domain.AccountRole;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Base class for integration tests. One container, one context, clean data.
 *
 * <p><strong>One container for the whole suite.</strong> The container is a {@code static} field
 * started once in a static initialiser and never stopped by JUnit — the documented
 * singleton-container pattern. Testcontainers' resource reaper removes it when the JVM exits.
 * Declaring {@code @Container} instead would tie its lifecycle to a single test class, so the
 * second class to run would find a stopped container.
 *
 * <p><strong>One context configuration.</strong> Every integration test inherits this exact
 * annotation set, so the framework builds and caches a single context for all of them. Varying the
 * configuration per class — different properties, different web environment — multiplies context
 * builds and comes to dominate suite time. A random web port is used for every test, including
 * those that do not make HTTP calls, because one shared configuration is worth more than a
 * marginally cheaper context for some of them.
 *
 * <p><strong>Data isolation: clean before, not after.</strong> Every collection is dropped before
 * each test. Cleaning afterwards instead would depend on teardown actually running, and a test that
 * crashes or times out would leave state behind for the next one — reintroducing exactly the order
 * dependence this class exists to remove. Cleaning first also leaves the failing test's data in
 * place for inspection.
 *
 * <p><strong>This is the only file that names a Testcontainers MongoDB type.</strong> Keep it that
 * way. Testcontainers 2.x relocates container classes into module-specific packages, and Epic #89
 * will make that move; concentrating the reference here turns that migration into a one-line change
 * no matter how many integration tests exist by then.
 *
 * <p><strong>A replica set, as in production.</strong> A container is standalone unless asked, and
 * a standalone hides what production gives. The URL it hands over names no {@code replicaSet}, so
 * the tests connect directly where production discovers members: {@code [facts]} in the data README
 * records what that leaves untested.
 *
 * <p><strong>Accounts: made up per run.</strong> The password is random, so no credential is
 * committed. The first Admin is configured with it, and the same Admin and a Scorekeeper are
 * written back after each drop, with a low cost hash so a request stays cheap in a suite of
 * hundreds.
 *
 * <p><strong>Active profile: test.</strong> The {@code @ActiveProfiles("test")} annotation
 * activates the test profile for all integration tests, ensuring test-specific configuration is
 * used while preserving a single shared context across the suite.
 */
@SpringBootTest(classes = KumiteGameStarter.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

  private static final String MONGO_IMAGE = "mongo:8.0";
  private static final int MONGO_PORT = 27017;
  private static final int CHEAP_HASH_COST = 4;

  public static final String ADMIN = "test-admin";
  public static final String SCOREKEEPER = "test-scorekeeper";
  public static final String PASSWORD = UUID.randomUUID().toString();

  private static final String STORED_HASH =
      "{bcrypt}" + new BCryptPasswordEncoder(CHEAP_HASH_COST).encode(PASSWORD);

  @ServiceConnection
  static final MongoDBContainer MONGO = new MongoDBContainer(MONGO_IMAGE).withReplicaSet();

  static {
    MONGO.start();
  }

  @Autowired protected MongoTemplate mongoTemplate;

  @Autowired private TestRestTemplate anonymous;

  @DynamicPropertySource
  static void firstAdmin(DynamicPropertyRegistry registry) {
    firstAdminSettings().forEach((key, value) -> registry.add(key, () -> value));
  }

  private static Map<String, String> firstAdminSettings() {
    return Map.of(
        "accounts.first-admin.username", ADMIN, "accounts.first-admin.password", PASSWORD);
  }

  /** The Scorekeeper, for an application started by hand, which only creates the Admin. */
  public static Account scorekeeperAccount() {
    return new Account(SCOREKEEPER, STORED_HASH, AccountRole.SCOREKEEPER);
  }

  /** The value of an {@code Authorization} header. */
  public static String basic(String username, String password) {
    String pair = username + ":" + password;
    return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
  }

  public static String adminCredentials() {
    return basic(ADMIN, PASSWORD);
  }

  /** The shared client, signed in as the Admin. */
  protected TestRestTemplate asAdmin() {
    return anonymous.withBasicAuth(ADMIN, PASSWORD);
  }

  protected TestRestTemplate asScorekeeper() {
    return anonymous.withBasicAuth(SCOREKEEPER, PASSWORD);
  }

  /** The shared replica set, for the tests that start the application by hand. */
  public static String replicaSetUri() {
    return MONGO.getReplicaSetUrl();
  }

  /** A standalone server, weaker than the shared one, for the tests that must be refused. */
  public static GenericContainer<?> standaloneMongo() {
    return new MongoDBContainer(MONGO_IMAGE);
  }

  /** A replica set of its own, for a test that must stop it answering. Started, and initiated. */
  public static GenericContainer<?> replicaSet() {
    GenericContainer<?> mongo = new MongoDBContainer(MONGO_IMAGE).withReplicaSet();
    mongo.start();
    return mongo;
  }

  /** A started replica set that requires a login, for the tests of rejected credentials. */
  public static GenericContainer<?> authenticatedMongo(String user, String password) {
    GenericContainer<?> mongo =
        new GenericContainer<>(MONGO_IMAGE)
            .withEnv("MONGO_INITDB_ROOT_USERNAME", user)
            .withEnv("MONGO_INITDB_ROOT_PASSWORD", password)
            .withCreateContainerCmdModifier(command -> command.withEntrypoint("bash"))
            .withCommand(
                "-c",
                "openssl rand -base64 756 > /tmp/key && chmod 400 /tmp/key"
                    + " && chown mongodb /tmp/key"
                    + " && exec docker-entrypoint.sh mongod --replSet rs0 --keyFile /tmp/key")
            .withExposedPorts(MONGO_PORT)
            .waitingFor(Wait.forLogMessage(".*Waiting for connections.*\\n", 2));
    mongo.start();
    mongosh(mongo, user, password, "rs.initiate()");
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> mongosh(mongo, user, password, "db.hello().isWritablePrimary").contains("true"));
    return mongo;
  }

  private static String mongosh(
      GenericContainer<?> mongo, String user, String password, String script) {
    try {
      return mongo
          .execInContainer(
              "mongosh",
              "-u",
              user,
              "-p",
              password,
              "--authenticationDatabase",
              "admin",
              "--quiet",
              "--eval",
              script)
          .getStdout();
    } catch (IOException e) {
      throw new IllegalStateException("mongosh could not run", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("mongosh was interrupted", e);
    }
  }

  /**
   * Starts the application by hand, for tests where a failed start is the expected result.
   *
   * <p>Arguments, because the builder's own properties rank below application.properties.
   */
  public static ConfigurableApplicationContext startApplication(
      String uri, Map<String, String> settings) {
    return startApplication("test", uri, settings);
  }

  /** The same, under a profile a real deployment uses, for what differs between them. */
  public static ConfigurableApplicationContext startApplication(
      String profile, String uri, Map<String, String> settings) {
    Map<String, String> all = new LinkedHashMap<>();
    all.put("server.port", "0");
    all.put("spring.mongodb.uri", uri);
    all.putAll(firstAdminSettings());
    all.putAll(settings);
    String[] args =
        all.entrySet().stream()
            .map(setting -> "--" + setting.getKey() + "=" + setting.getValue())
            .toArray(String[]::new);
    return new SpringApplicationBuilder(KumiteGameStarter.class).profiles(profile).run(args);
  }

  /** Where an application started by hand answers. */
  public static String addressOf(ConfigurableApplicationContext context) {
    return "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
  }

  /**
   * A request to an application started by hand.
   *
   * <p>On {@link HttpClient}, because it sends {@code Origin} as given. The older JDK client
   * silently drops it.
   */
  public static HttpRequest.Builder request(ConfigurableApplicationContext context, String path) {
    return HttpRequest.newBuilder(URI.create(addressOf(context) + path));
  }

  public static HttpResponse<String> send(HttpRequest.Builder request)
      throws IOException, InterruptedException {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(request.build(), BodyHandlers.ofString());
    }
  }

  /** A replica set member nobody initiated, for the tests that must be refused. */
  public static GenericContainer<?> uninitiatedMongo() {
    return new GenericContainer<>(MONGO_IMAGE)
        .withCommand("--replSet", "rs0")
        .withExposedPorts(MONGO_PORT)
        .waitingFor(Wait.forLogMessage(".*Waiting for connections.*\\n", 1));
  }

  @BeforeEach
  void dropEveryCollection() {
    mongoTemplate.getCollectionNames().forEach(mongoTemplate::dropCollection);
    mongoTemplate.insert(new Account(ADMIN, STORED_HASH, AccountRole.ADMIN));
    mongoTemplate.insert(scorekeeperAccount());
  }
}

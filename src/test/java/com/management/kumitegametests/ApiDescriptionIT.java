package com.management.kumitegametests;

import static com.management.testsupport.IntegrationTestBase.request;
import static com.management.testsupport.IntegrationTestBase.send;
import static org.assertj.core.api.Assertions.assertThat;

import com.management.enums.PlayerColor;
import com.management.enums.PointsType;
import com.management.testsupport.IntegrationTestBase;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The generated description, read from the real application and compared with what it really does.
 *
 * <p>Nothing here reads a copy of the description. The expected routes come from the live mappings
 * and the expected schemas from the {@code dto} package, so a later endpoint or request type cannot
 * go missing unnoticed.
 */
class ApiDescriptionIT {

  private static final String DESCRIPTION = "/v3/api-docs";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";
  private static final String PLAYERS = "/api/v1/players";
  private static final String MATCHES = "/api/v1/kumitegame";
  private static final String MATCH = MATCHES + "/{gameId}";
  private static final String PLAYER = PLAYERS + "/{playerId}";
  private static final String CONTROLLERS = "com.management.controllers";
  private static final String NEW_MATCH =
      """
      {
        "playersMap": {
          "first": {"name": "Aka", "color": "RED"},
          "second": {"name": "Ao", "color": "BLUE"}
        },
        "refereeList": ["Sato"]
      }
      """;

  private static final String ACCOUNTS = "/api/v1/accounts";

  private static ConfigurableApplicationContext dev;
  private static ConfigurableApplicationContext prod;
  private static JsonNode description;

  @BeforeAll
  static void readTheDescription() throws Exception {
    String mongo = IntegrationTestBase.replicaSetUri();
    dev = IntegrationTestBase.startApplication("dev", mongo, Map.of());
    prod = IntegrationTestBase.startApplication("prod", mongo, Map.of());
    HttpResponse<String> response = send(request(dev, DESCRIPTION).GET());
    assertThat(response.statusCode()).isEqualTo(200);
    description = JsonMapper.shared().readTree(response.body());
  }

  @AfterAll
  static void stop() {
    dev.close();
    prod.close();
  }

  @Test
  void description_listsExactlyTheRealEndpoints() {
    Set<String> real = realEndpoints();

    assertThat(real).contains("GET " + PLAYER, "PUT " + MATCH + "/add-time");
    assertThat(describedEndpoints()).containsExactlyInAnyOrderElementsOf(real);
  }

  @Test
  void everyFailure_isProblemDetail() {
    List<JsonNode> operations = operations();

    assertThat(operations).hasSameSizeAs(realEndpoints());
    assertThat(operations)
        .allSatisfy(operation -> {
          assertThat(statusesOf(operation)).contains("500");
          operation.get("responses").properties().stream()
              .filter(response -> response.getKey().compareTo("400") >= 0)
              .forEach(response -> {
                JsonNode content = response.getValue().get("content");
                assertThat(content.propertyNames()).containsExactly(PROBLEM_JSON);
                assertThat(content.get(PROBLEM_JSON).get("schema").get("$ref").asString())
                    .isEqualTo(PROBLEM_REF);
              });
        });
  }

  @Test
  void failures_matchWhatEachKindOfOperationCanMeet() {
    assertThat(statusesOf(operation("post", PLAYERS)))
        .containsExactly("201", "400", "401", "403", "413", "415", "500");
    assertThat(statusesOf(operation("get", PLAYER))).containsExactly("200", "401", "404", "500");
    assertThat(statusesOf(operation("delete", PLAYER)))
        .containsExactly("204", "401", "403", "404", "409", "500");
    assertThat(statusesOf(operation("put", MATCH + "/add-foul")))
        .containsExactly("200", "400", "401", "404", "409", "500");
    assertThat(statusesOf(operation("put", MATCH + "/add-time")))
        .containsExactly("200", "400", "401", "404", "409", "413", "415", "500");
  }

  @Test
  void basicScheme_isDescribed() {
    JsonNode scheme = description.get("components").get("securitySchemes").get("basicAuth");

    assertThat(scheme.get("type").asString()).isEqualTo("http");
    assertThat(scheme.get("scheme").asString()).isEqualTo("basic");
  }

  @Test
  void everyOperationButPublicReads_needsTheScheme() {
    assertThat(operation("get", PLAYER).has("security")).isFalse();
    assertThat(operation("get", MATCH).has("security")).isFalse();
    assertThat(operation("post", PLAYERS).get("security").toString()).contains("basicAuth");
    assertThat(operation("put", MATCH + "/add-foul").get("security").toString())
        .contains("basicAuth");
  }

  /** All three come from the real chain, so the description cannot claim what it never does. */
  @Test
  void authenticationFailures_matchTheRealAnswers() throws Exception {
    dev.getBean(MongoTemplate.class).save(IntegrationTestBase.scorekeeperAccount());
    String wrong = IntegrationTestBase.basic(IntegrationTestBase.ADMIN, "wrong");
    String scorekeeper =
        IntegrationTestBase.basic(IntegrationTestBase.SCOREKEEPER, IntegrationTestBase.PASSWORD);
    HttpResponse<String> publicRead =
        send(request(dev, PLAYERS + "/none").header("Authorization", wrong).GET());
    HttpResponse<String> write = send(request(dev, PLAYERS).POST(BodyPublishers.ofString("{}")));
    HttpResponse<String> refused =
        send(
            request(dev, PLAYERS)
                .header("Authorization", scorekeeper)
                .POST(BodyPublishers.ofString("{}")));

    assertThat(statusesOf(operation("get", PLAYER)))
        .contains(String.valueOf(publicRead.statusCode()));
    assertThat(statusesOf(operation("post", PLAYERS)))
        .contains(String.valueOf(write.statusCode()), String.valueOf(refused.statusCode()));
    assertThat(publicRead.statusCode()).isEqualTo(401);
    assertThat(write.statusCode()).isEqualTo(401);
    assertThat(refused.statusCode()).isEqualTo(403);
    assertThat(fieldsOf(write.body()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("ProblemDetail").get("required")));
    assertThat(fieldsOf(refused.body()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("ProblemDetail").get("required")));
  }

  @Test
  void successes_matchTheRealAnswers() throws Exception {
    HttpResponse<String> created = createPlayer();
    String location = created.headers().firstValue("Location").orElseThrow();

    assertThat(statusesOf(operation("post", PLAYERS)))
        .contains(String.valueOf(created.statusCode()));
    assertThat(statusesOf(operation("get", PLAYER)))
        .contains(String.valueOf(send(request(dev, location).GET()).statusCode()));
    assertThat(statusesOf(operation("delete", PLAYER)))
        .contains(String.valueOf(send(asAdmin(request(dev, location)).DELETE()).statusCode()));
    assertThat(response("delete", PLAYER, "204").has("content")).isFalse();
    assertThat(response("get", PLAYER, "200").get("content").propertyNames())
        .containsExactly("application/json");
  }

  @Test
  void accountOperations_listWhatTheyCanMeet() {
    assertThat(statusesOf(operation("post", ACCOUNTS)))
        .containsExactly("201", "400", "401", "403", "409", "413", "415", "500");
    assertThat(statusesOf(operation("delete", ACCOUNTS + "/{username}")))
        .containsExactly("204", "401", "403", "404", "409", "500");
    assertThat(response("post", ACCOUNTS, "201").get("headers").propertyNames())
        .contains("Location");
  }

  /** The request has a password, and the response must never have room for one. */
  @Test
  void accountResponse_describesNoPassword() {
    assertThat(schema("AccountResponse").get("properties").propertyNames())
        .containsExactlyInAnyOrder("username", "role");
    assertThat(schema("AccountRequest").get("properties").get("password").get("format").asString())
        .isEqualTo("password");
  }

  /** Who recorded a change comes from the signed-in account, so no body has room for it. */
  @Test
  void requests_haveNoRoomForTheOperator() {
    assertThat(schema("WinnerOverrideRequest").get("properties").propertyNames())
        .containsExactlyInAnyOrder("winner", "reason", "referee");
    assertThat(schema("ClockAdjustmentRequest").get("properties").propertyNames())
        .containsExactly("seconds");
  }

  @Test
  void signedInIdentity_staysOutOfTheParameters() {
    for (String path : List.of(MATCH + "/add-time", MATCH + "/override-winner")) {
      List<String> names = new ArrayList<>();
      operation("put", path).get("parameters").forEach(p -> names.add(p.get("name").asString()));

      assertThat(names).containsExactly("gameId");
    }
  }

  @Test
  void headers_areDescribed() {
    assertThat(response("post", MATCHES, "201").get("headers").propertyNames())
        .contains("Location", "X-Correlation-ID");
    assertThat(response("get", PLAYER, "404").get("headers").propertyNames())
        .containsExactly("X-Correlation-ID");
  }

  @Test
  void problemSchema_listsWhatRealFailuresSend() throws Exception {
    HttpResponse<String> missing = send(request(dev, PLAYERS + "/none").GET());
    HttpResponse<String> invalid =
        send(
            asAdmin(request(dev, PLAYERS))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{}")));

    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(invalid.statusCode()).isEqualTo(400);
    assertThat(missing.headers().firstValue("Content-Type")).contains(PROBLEM_JSON);
    assertThat(fieldsOf(invalid.body()))
        .containsExactlyInAnyOrderElementsOf(
            schema("ProblemDetail").get("properties").propertyNames());
    assertThat(fieldsOf(missing.body()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("ProblemDetail").get("required")));
  }

  @Test
  void schemas_areExactlyTheApiShapes() {
    Set<String> shapes =
        new ClassFileImporter()
            .importPackages("com.management.dto").stream()
                .map(JavaClass::getSimpleName)
                .filter(name -> !"package-info".equals(name))
                .collect(Collectors.toSet());

    assertThat(shapes).contains("PlayerRequest", "KumiteGameResponse");
    assertThat(description.get("components").get("schemas").propertyNames())
        .containsExactlyInAnyOrderElementsOf(
            Stream.concat(shapes.stream(), Stream.of("ProblemDetail")).toList());
  }

  @Test
  void matchSchema_describesRealMatches() throws Exception {
    HttpResponse<String> created =
        send(
            asAdmin(request(dev, MATCHES))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(NEW_MATCH)));
    JsonNode match = JsonMapper.shared().readTree(created.body());
    JsonNode schema = schema("KumiteGameResponse");

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(fieldsOf(created.body()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema.get("required")));
    assertThat(match.get("winner").isNull()).isTrue();
    assertThat(match.get("endReason").isNull()).isTrue();
    assertThat(namesOf(schema.get("properties").get("winner").get("enum"))).contains("null");
    assertThat(namesOf(schema.get("properties").get("endReason").get("enum"))).contains("null");
    assertThat(fieldsOf(match.get("red").toString()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("PlayerSummary").get("required")));
  }

  @Test
  void colourAndPointType_listTheirValues() {
    List<String> colours = names(PlayerColor.values());
    List<String> pointTypes = names(PointsType.values());

    List<JsonNode> parameters = new ArrayList<>();
    operations().forEach(operation -> operation.path("parameters").forEach(parameters::add));

    assertThat(parameters.stream().filter(parameter -> named(parameter, "color")))
        .hasSize(7)
        .allSatisfy(colour ->
            assertThat(namesOf(colour.get("schema").get("enum")))
                .containsExactlyElementsOf(colours));
    assertThat(parameters.stream().filter(parameter -> named(parameter, "pointType")))
        .hasSize(2)
        .allSatisfy(type ->
            assertThat(namesOf(type.get("schema").get("enum")))
                .containsExactlyElementsOf(pointTypes));
    assertThat(namesOf(schema("MatchFighterRequest").get("properties").get("color").get("enum")))
        .containsExactlyElementsOf(colours);
    assertThat(namesOf(schema("WinnerOverrideRequest").get("properties").get("winner").get("enum")))
        .containsAll(colours)
        .contains("null");
  }

  @Test
  void description_isNotMovedUnderTheApiVersion() throws Exception {
    assertThat(send(request(dev, "/api/v1" + DESCRIPTION).GET()).statusCode()).isEqualTo(401);
  }

  @Test
  void description_servesYamlThoughTheApiDoesNot() throws Exception {
    assertThat(send(request(dev, DESCRIPTION + ".yaml").GET()).statusCode()).isEqualTo(200);
  }

  @Test
  void description_isOutsideCorsAndInsideCorrelation() throws Exception {
    HttpResponse<String> response =
        send(request(dev, DESCRIPTION).header("Origin", "http://localhost:5173").GET());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    assertThat(response.headers().firstValue("X-Correlation-ID")).isPresent();
  }

  @ParameterizedTest
  @ValueSource(strings = {DESCRIPTION, DESCRIPTION + ".yaml"})
  void prod_servesNoDescription(String path) throws Exception {
    assertThat(send(request(prod, path).GET()).statusCode()).isEqualTo(404);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/swagger-ui.html", "/swagger-ui/index.html"})
  void noProfile_servesAnInterface(String path) throws Exception {
    assertThat(send(asAdmin(request(dev, path)).GET()).statusCode()).isEqualTo(403);
    assertThat(send(asAdmin(request(prod, path)).GET()).statusCode()).isEqualTo(403);
  }

  private static Set<String> realEndpoints() {
    return dev
        .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
        .getHandlerMethods()
        .entrySet()
        .stream()
        .filter(mapping -> mapping.getValue().getBeanType().getPackageName().equals(CONTROLLERS))
        .flatMap(mapping ->
            mapping.getKey().getMethodsCondition().getMethods().stream()
                .flatMap(method ->
                    mapping.getKey().getPatternValues().stream()
                        .map(path -> method.name() + " " + path)))
        .collect(Collectors.toSet());
  }

  private static List<String> describedEndpoints() {
    return description.get("paths").properties().stream()
        .flatMap(path ->
            path.getValue().propertyNames().stream()
                .map(method -> method.toUpperCase(Locale.ROOT) + " " + path.getKey()))
        .toList();
  }

  private static List<JsonNode> operations() {
    return description.get("paths").properties().stream()
        .flatMap(path -> path.getValue().properties().stream().map(Map.Entry::getValue))
        .toList();
  }

  private static JsonNode operation(String method, String path) {
    return description.get("paths").get(path).get(method);
  }

  private static JsonNode response(String method, String path, String status) {
    return operation(method, path).get("responses").get(status);
  }

  private static JsonNode schema(String name) {
    return description.get("components").get("schemas").get(name);
  }

  private static List<String> statusesOf(JsonNode operation) {
    return operation.get("responses").propertyNames().stream().sorted().toList();
  }

  private static boolean named(JsonNode parameter, String name) {
    return name.equals(parameter.get("name").asString());
  }

  private static List<String> namesOf(JsonNode array) {
    List<String> names = new ArrayList<>();
    array.forEach(value -> names.add(value.isNull() ? "null" : value.asString()));
    return names;
  }

  private static List<String> names(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }

  private static Set<String> fieldsOf(String body) {
    return Set.copyOf(JsonMapper.shared().readTree(body).propertyNames());
  }

  private static HttpResponse<String> createPlayer() throws IOException, InterruptedException {
    return send(
        asAdmin(request(dev, PLAYERS))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{\"name\":\"Kenji\"}")));
  }

  private static HttpRequest.Builder asAdmin(HttpRequest.Builder request) {
    return request.header("Authorization", IntegrationTestBase.adminCredentials());
  }
}

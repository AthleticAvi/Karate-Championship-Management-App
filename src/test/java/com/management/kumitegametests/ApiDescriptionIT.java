package com.management.kumitegametests;

import static com.management.testsupport.IntegrationTestBase.request;
import static com.management.testsupport.IntegrationTestBase.send;
import static org.assertj.core.api.Assertions.assertThat;

import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.Side;
import com.management.testsupport.FeatureWeb;
import com.management.testsupport.IntegrationTestBase;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * and the expected schemas from the records in each feature's web package, so a later endpoint or
 * request type cannot go missing unnoticed.
 */
class ApiDescriptionIT {

  private static final String DESCRIPTION = "/v3/api-docs";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";
  private static final String ATHLETES = "/api/v1/athletes";
  private static final String GAMES = "/api/v1/kumite-games";
  private static final String GAME = GAMES + "/{gameId}";
  private static final String FULL_VIEW = GAME + "/full-view";
  private static final String PUBLIC_VIEW = "_Public";
  private static final String ATHLETE = ATHLETES + "/{athleteId}";
  private static final String REFEREES = "/api/v1/referees";
  private static final String REFEREE = REFEREES + "/{refereeId}";

  /** How the generator names the framework's page of a shape, and what it says of the page. */
  private static final String PAGE = "PagedModel";

  private static final String PAGE_METADATA = "PageMetadata";

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

    assertThat(real).contains("GET " + ATHLETE, "POST " + GAME + "/scores");
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
    assertThat(statusesOf(operation("post", ATHLETES)))
        .containsExactly("201", "400", "401", "403", "413", "415", "500");
    assertThat(statusesOf(operation("get", ATHLETE))).containsExactly("200", "401", "404", "500");
    assertThat(statusesOf(operation("delete", ATHLETE)))
        .containsExactly("204", "401", "403", "404", "409", "500");
    for (String read : List.of(GAME, FULL_VIEW)) {
      assertThat(statusesOf(operation("get", read)))
          .as("a tagged read states its 304")
          .containsExactly("200", "304", "401", "404", "500");
      assertThat(response("get", read, "200").get("headers").propertyNames()).contains("ETag");
      assertThat(response("get", read, "304").get("headers").propertyNames()).contains("ETag");
      assertThat(response("get", read, "304").has("content")).isFalse();
    }
    for (String status : List.of("200", "304")) {
      assertThat(response("get", GAME, status).get("headers").propertyNames())
          .as("the public view states the policy that lets a browser keep it")
          .contains("Cache-Control");
    }
    assertThat(statusesOf(operation("post", GAMES)))
        .containsExactly("201", "400", "401", "413", "415", "500");
    assertThat(statusesOf(operation("post", GAME + "/scores")))
        .containsExactly("200", "400", "401", "404", "409", "413", "415", "500");
    assertThat(statusesOf(operation("post", GAME + "/senshu-records")))
        .containsExactly("200", "400", "401", "404", "409", "413", "415", "500");
    assertThat(response("put", GAME + "/settings", "409").get("description").asString())
        .contains("current");
    assertThat(response("post", GAME + "/clock-actions", "409").get("description").asString())
        .contains("current");
    assertThat(response("post", GAME + "/results", "409").get("description").asString())
        .contains("current");
    assertThat(statusesOf(operation("post", GAME + "/status-changes")))
        .as("a Scorekeeper row that states the 403 the game answers for an Admin's move")
        .containsExactly("200", "400", "401", "403", "404", "409", "413", "415", "500");
    assertThat(response("post", GAME + "/status-changes", "409").get("description").asString())
        .contains("current");
    assertThat(statusesOf(operation("get", REFEREE))).containsExactly("200", "401", "404", "500");
    assertThat(statusesOf(operation("put", REFEREE)))
        .containsExactly("200", "400", "401", "403", "404", "409", "413", "415", "500");
    assertThat(response("put", REFEREE, "409").get("description").asString()).contains("current");
  }

  @Test
  void basicScheme_isDescribed() {
    JsonNode scheme = description.get("components").get("securitySchemes").get("basicAuth");

    assertThat(scheme.get("type").asString()).isEqualTo("http");
    assertThat(scheme.get("scheme").asString()).isEqualTo("basic");
  }

  @Test
  void everyOperationButPublicReads_needsTheScheme() {
    assertThat(operation("get", ATHLETE).has("security")).isFalse();
    assertThat(operation("get", GAME).has("security")).isFalse();
    assertThat(operation("get", FULL_VIEW).get("security").toString()).contains("basicAuth");
    assertThat(operation("post", ATHLETES).get("security").toString()).contains("basicAuth");
    assertThat(operation("post", GAME + "/scores").get("security").toString())
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
        send(request(dev, ATHLETES + "/none").header("Authorization", wrong).GET());
    HttpResponse<String> write = send(request(dev, ATHLETES).POST(BodyPublishers.ofString("{}")));
    HttpResponse<String> refused =
        send(
            request(dev, ATHLETES)
                .header("Authorization", scorekeeper)
                .POST(BodyPublishers.ofString("{}")));

    assertThat(statusesOf(operation("get", ATHLETE)))
        .contains(String.valueOf(publicRead.statusCode()));
    assertThat(statusesOf(operation("post", ATHLETES)))
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
    HttpResponse<String> created = createAthlete();
    String location = created.headers().firstValue("Location").orElseThrow();

    assertThat(statusesOf(operation("post", ATHLETES)))
        .contains(String.valueOf(created.statusCode()));
    assertThat(statusesOf(operation("get", ATHLETE)))
        .contains(String.valueOf(send(request(dev, location).GET()).statusCode()));
    assertThat(statusesOf(operation("delete", ATHLETE)))
        .contains(String.valueOf(send(asAdmin(request(dev, location)).DELETE()).statusCode()));
    assertThat(response("delete", ATHLETE, "204").has("content")).isFalse();
    assertThat(response("get", ATHLETE, "200").get("content").propertyNames())
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
    for (String request :
        List.of(
            "ScoreRequest",
            "PenaltyRequest",
            "ClockActionRequest",
            "SenshuRecordRequest",
            "AssignmentRequest",
            "AmendmentRequest",
            "ResultRequest",
            "StatusChangeRequest",
            "KumiteGameRequest")) {
      assertThat(schema(request).get("properties").propertyNames())
          .doesNotContain("recordedBy", "recordedAt", "order");
    }
  }

  @Test
  void signedInIdentity_staysOutOfTheParameters() {
    for (String path : List.of(GAME + "/scores", GAME + "/results", GAME + "/status-changes")) {
      List<String> names = new ArrayList<>();
      operation("post", path).get("parameters").forEach(p -> names.add(p.get("name").asString()));

      assertThat(names).containsExactly("gameId");
    }
    for (String read : List.of(GAME, FULL_VIEW)) {
      List<String> names = new ArrayList<>();
      operation("get", read).get("parameters").forEach(p -> names.add(p.get("name").asString()));

      assertThat(names).as("the request a tag is checked against").containsExactly("gameId");
    }
  }

  @Test
  void headers_areDescribed() {
    assertThat(response("post", GAMES, "201").get("headers").propertyNames())
        .contains("Location", "X-Correlation-ID");
    assertThat(response("get", ATHLETE, "404").get("headers").propertyNames())
        .containsExactly("X-Correlation-ID");
  }

  @Test
  void problemSchema_listsWhatRealFailuresSend() throws Exception {
    HttpResponse<String> missing = send(request(dev, ATHLETES + "/none").GET());
    HttpResponse<String> invalid =
        send(
            asAdmin(request(dev, ATHLETES))
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
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.management")
                .stream()
                .filter(type -> FeatureWeb.holds(type.getPackageName()) && type.isRecord())
                .map(JavaClass::getSimpleName)
                .collect(Collectors.toSet());

    assertThat(shapes).contains("AthleteRequest", "KumiteGameResponse", "ScoreResponse");
    Collection<String> described = description.get("components").get("schemas").propertyNames();
    Set<String> publicView =
        described.stream().filter(name -> name.endsWith(PUBLIC_VIEW)).collect(Collectors.toSet());
    Set<String> pages =
        described.stream().filter(name -> name.startsWith(PAGE)).collect(Collectors.toSet());
    assertThat(described)
        .containsExactlyInAnyOrderElementsOf(
            Stream.of(
                    shapes.stream(),
                    publicView.stream(),
                    pages.stream(),
                    Stream.of("ProblemDetail", PAGE_METADATA))
                .flatMap(Function.identity())
                .toList());
    assertThat(publicView)
        .as("the generator names a schema reached through the public view after the view")
        .contains("KumiteGameResponse" + PUBLIC_VIEW, "EntryMetaResponse" + PUBLIC_VIEW)
        .allSatisfy(name ->
            assertThat(shapes).contains(name.substring(0, name.length() - PUBLIC_VIEW.length())));
    assertThat(pages)
        .as("the framework's page of a shape is named after the shape it holds")
        .containsExactlyInAnyOrder(
            PAGE + "AthleteResponse", PAGE + "RefereeResponse", PAGE + "KumiteGameSummaryResponse")
        .allSatisfy(name -> assertThat(shapes).contains(name.substring(PAGE.length())));
  }

  /** A list takes the page, its size and its filters, and never an order: the server keeps one. */
  @Test
  void lists_arePublic_takeTheirFilters_andNoOrder() {
    assertThat(statusesOf(operation("get", ATHLETES))).containsExactly("200", "401", "500");
    assertThat(statusesOf(operation("get", REFEREES))).containsExactly("200", "401", "500");
    assertThat(statusesOf(operation("get", GAMES)))
        .as("a status that does not exist is refused")
        .containsExactly("200", "400", "401", "500");
    assertThat(parametersOf(operation("get", ATHLETES)))
        .containsExactlyInAnyOrder("name", "page", "size");
    assertThat(parametersOf(operation("get", REFEREES)))
        .containsExactlyInAnyOrder("name", "page", "size");
    assertThat(parametersOf(operation("get", GAMES)))
        .containsExactlyInAnyOrder("status", "athleteId", "page", "size");
    for (String list : List.of(ATHLETES, REFEREES, GAMES)) {
      assertThat(operation("get", list).has("security")).as(list).isFalse();
    }
  }

  /** The page and each summary, as really sent, against the schemas the description states. */
  @Test
  void pageSchema_describesRealLists() throws Exception {
    String aka = JsonMapper.shared().readTree(createAthlete().body()).get("id").asString();
    String ao = JsonMapper.shared().readTree(createAthlete().body()).get("id").asString();
    send(
        asAdmin(request(dev, GAMES))
            .header("Content-Type", "application/json")
            .POST(
                BodyPublishers.ofString(
                    """
                    {"format": "ELIMINATION", "medalGame": false, "extraGame": false,
                     "akaAthleteId": "%s", "aoAthleteId": "%s"}
                    """
                        .formatted(aka, ao))));
    HttpResponse<String> list = send(request(dev, GAMES).GET());
    JsonNode page = JsonMapper.shared().readTree(list.body());

    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(
            response("get", GAMES, "200")
                .get("content")
                .get("application/json")
                .get("schema")
                .get("$ref")
                .asString())
        .endsWith(PAGE + "KumiteGameSummaryResponse");
    assertThat(fieldsOf(list.body()))
        .containsExactlyInAnyOrderElementsOf(
            namesOf(schema(PAGE + "KumiteGameSummaryResponse").get("required")));
    assertThat(fieldsOf(page.get("page").toString()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema(PAGE_METADATA).get("required")));
    JsonNode summary = page.get("content").get(0);
    assertThat(fieldsOf(summary.toString()))
        .containsExactlyInAnyOrderElementsOf(
            namesOf(schema("KumiteGameSummaryResponse").get("required")));
    assertThat(fieldsOf(summary.get("aka").toString()))
        .containsExactlyInAnyOrderElementsOf(
            namesOf(schema("SideSummaryResponse").get("required")));
    assertThat(summary.get("currentResult").isNull()).isTrue();
  }

  /** The public view's schema is the game less the recorder and the warnings, as really sent. */
  @Test
  void publicViewSchema_describesRealPublicAnswers() throws Exception {
    String aka = JsonMapper.shared().readTree(createAthlete().body()).get("id").asString();
    String ao = JsonMapper.shared().readTree(createAthlete().body()).get("id").asString();
    HttpResponse<String> created =
        send(
            asAdmin(request(dev, GAMES))
                .header("Content-Type", "application/json")
                .POST(
                    BodyPublishers.ofString(
                        """
                        {"format": "ELIMINATION", "medalGame": false, "extraGame": false,
                         "akaAthleteId": "%s", "aoAthleteId": "%s"}
                        """
                            .formatted(aka, ao))));
    String gameId = JsonMapper.shared().readTree(created.body()).get("game").get("id").asString();
    HttpResponse<String> read = send(request(dev, GAMES + "/" + gameId).GET());
    JsonNode game = JsonMapper.shared().readTree(read.body());

    assertThat(read.statusCode()).isEqualTo(200);
    assertThat(statusesOf(operation("get", GAME))).contains(String.valueOf(read.statusCode()));
    assertThat(
            response("get", GAME, "200")
                .get("content")
                .get("application/json")
                .get("schema")
                .get("$ref")
                .asString())
        .endsWith("KumiteGameResponse" + PUBLIC_VIEW);
    assertThat(fieldsOf(read.body()))
        .containsExactlyInAnyOrderElementsOf(
            namesOf(schema("KumiteGameResponse" + PUBLIC_VIEW).get("required")))
        .doesNotContain("warnings");
    assertThat(fieldsOf(game.get("assignments").get(0).get("meta").toString()))
        .containsExactlyInAnyOrderElementsOf(
            namesOf(schema("EntryMetaResponse" + PUBLIC_VIEW).get("required")))
        .doesNotContain("recordedBy", "raisedWarnings");
  }

  /** A note in free text is described on the full view only. */
  @ParameterizedTest
  @CsvSource({
    "SenshuRecordResponse, reason",
    "AmendmentResponse, reason",
    "StatusChangeResponse, reason",
    "ResultResponse, reasonDetail"
  })
  void publicViewSchemas_leaveOutTheNotes(String record, String note) {
    assertThat(schema(record).get("properties").propertyNames()).contains(note);
    assertThat(namesOf(schema(record).get("required"))).contains(note);
    assertThat(schema(record + PUBLIC_VIEW).get("properties").propertyNames()).doesNotContain(note);
    assertThat(namesOf(schema(record + PUBLIC_VIEW).get("required"))).doesNotContain(note);
  }

  /** The enums a scoreboard shows stay in the public view. */
  @Test
  void publicViewSchemas_keepTheEnums() {
    assertThat(schema("ResultResponse" + PUBLIC_VIEW).get("properties").propertyNames())
        .contains("reason");
    assertThat(schema("ClockActionResponse" + PUBLIC_VIEW).get("properties").propertyNames())
        .contains("pauseReason");
  }

  @Test
  void gameSchema_describesRealGames() throws Exception {
    String aka = JsonMapper.shared().readTree(createAthlete().body()).get("id").asString();
    String ao = JsonMapper.shared().readTree(createAthlete().body()).get("id").asString();
    HttpResponse<String> created =
        send(
            asAdmin(request(dev, GAMES))
                .header("Content-Type", "application/json")
                .POST(
                    BodyPublishers.ofString(
                        """
                        {"format": "ELIMINATION", "medalGame": false, "extraGame": false,
                         "akaAthleteId": "%s", "aoAthleteId": "%s"}
                        """
                            .formatted(aka, ao))));
    JsonNode game = JsonMapper.shared().readTree(created.body()).get("game");

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(fieldsOf(created.body()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("RecordedResponse").get("required")));
    assertThat(fieldsOf(game.toString()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("KumiteGameResponse").get("required")));
    assertThat(fieldsOf(game.get("aka").toString()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("SideResponse").get("required")));
    assertThat(fieldsOf(game.get("assignments").get(0).get("meta").toString()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("EntryMetaResponse").get("required")));
    assertThat(game.get("currentResult").isNull()).isTrue();
  }

  @Test
  void sideAndScoreLevel_listTheirValues() {
    JsonNode score = schema("ScoreRequest").get("properties");

    assertThat(namesOf(score.get("side").get("enum")))
        .containsExactlyElementsOf(names(Side.values()));
    assertThat(namesOf(score.get("level").get("enum")))
        .containsExactlyElementsOf(names(ScoreLevel.values()));
    assertThat(namesOf(schema("ResultRequest").get("properties").get("winner").get("enum")))
        .containsAll(names(Side.values()))
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
        .filter(mapping -> FeatureWeb.holds(mapping.getValue().getBeanType().getPackageName()))
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

  private static List<String> parametersOf(JsonNode operation) {
    return operation.get("parameters").valueStream().map(p -> p.get("name").asString()).toList();
  }

  private static List<String> statusesOf(JsonNode operation) {
    return operation.get("responses").propertyNames().stream().sorted().toList();
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

  private static HttpResponse<String> createAthlete() throws IOException, InterruptedException {
    return send(
        asAdmin(request(dev, ATHLETES))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{\"name\":\"Kenji\"}")));
  }

  private static HttpRequest.Builder asAdmin(HttpRequest.Builder request) {
    return request.header("Authorization", IntegrationTestBase.adminCredentials());
  }
}

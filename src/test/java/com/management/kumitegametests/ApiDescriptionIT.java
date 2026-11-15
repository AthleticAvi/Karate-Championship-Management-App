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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

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

  /**
   * A value of each JSON type, sent where the schema does not take it. Text looks like a number, so
   * no conversion can hide it.
   */
  private static final Map<String, JsonNode> TYPE_SAMPLES = typeSamples();

  private static ConfigurableApplicationContext dev;
  private static ConfigurableApplicationContext prod;
  private static JsonNode description;
  private static Set<String> apiShapes;

  @BeforeAll
  static void readTheDescription() throws Exception {
    String mongo = IntegrationTestBase.replicaSetUri();
    dev = IntegrationTestBase.startApplication("dev", mongo, Map.of());
    prod = IntegrationTestBase.startApplication("prod", mongo, Map.of());
    HttpResponse<String> response = send(request(dev, DESCRIPTION).GET());
    assertThat(response.statusCode()).isEqualTo(200);
    description = JsonMapper.shared().readTree(response.body());
    apiShapes = readApiShapes();
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
        .contains("current", "expectedVersion");
    assertThat(response("post", GAME + "/clock-actions", "409").get("description").asString())
        .contains("current", "expectedVersion");
    assertThat(response("post", GAME + "/results", "409").get("description").asString())
        .contains("current", "expectedVersion");
    assertThat(statusesOf(operation("post", GAME + "/status-changes")))
        .as("a Scorekeeper row that states the 403 the game answers for an Admin's move")
        .containsExactly("200", "400", "401", "403", "404", "409", "413", "415", "500");
    assertThat(response("post", GAME + "/status-changes", "409").get("description").asString())
        .contains("current", "expectedVersion");
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
    Set<String> shapes = apiShapes;

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

  /**
   * The public view's schema is the game less the recorder, the warnings and the notes, as really
   * sent, and a side keeps its disqualification.
   */
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
    assertThat(fieldsOf(game.get("aka").toString()))
        .containsExactlyInAnyOrderElementsOf(
            namesOf(schema("SideResponse" + PUBLIC_VIEW).get("required")))
        .contains("hansokuCount", "shikkakuCount", "disqualified");
    assertThat(fieldsOf(game.get("settings").toString()))
        .containsExactlyInAnyOrderElementsOf(
            namesOf(schema("SettingsResponse" + PUBLIC_VIEW).get("required")))
        .contains("panelLimit");
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
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("SideResponse").get("required")))
        .contains("hansokuCount", "shikkakuCount", "disqualified");
    assertThat(fieldsOf(game.get("assignments").get(0).get("meta").toString()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("EntryMetaResponse").get("required")));
    assertThat(game.get("currentResult").isNull()).isTrue();
    assertThat(fieldsOf(game.get("settings").toString()))
        .containsExactlyInAnyOrderElementsOf(namesOf(schema("SettingsResponse").get("required")))
        .contains("panelLimit");
  }

  /**
   * A game stored before the panel limit existed has none, so the limit is described as such, in
   * both views.
   */
  @ParameterizedTest
  @ValueSource(strings = {"SettingsResponse", "SettingsResponse" + PUBLIC_VIEW})
  void settingsSchema_describesThePanelLimit_asAnIntegerOrNone(String settings) {
    assertThat(namesOf(schema(settings).get("properties").path("panelLimit").path("type")))
        .containsExactlyInAnyOrder("integer", "null");
  }

  /** An absolute action needs the version it was chosen against; an append takes none. */
  @Test
  void absoluteActions_requireTheVersionTheyWereChosenAgainst() {
    for (String request : List.of("SettingsRequest", "ResultRequest", "StatusChangeRequest")) {
      assertThat(namesOf(schema(request).get("required"))).contains("expectedVersion");
      assertThat(
              schema(request)
                  .get("properties")
                  .get("expectedVersion")
                  .get("description")
                  .asString())
          .contains("409");
    }
    JsonNode clockAction = schema("ClockActionRequest");
    assertThat(namesOf(clockAction.get("required"))).doesNotContain("expectedVersion");
    assertThat(clockAction.get("properties").get("expectedVersion").get("description").asString())
        .contains("SET_TIME");
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

  /**
   * Each record a request body reaches states that it takes no other field, and the set is exactly
   * the request records in the code. No response states it, so a response can gain a field.
   */
  @Test
  void requestSchemas_allowNoOtherFields_andResponseSchemasDoNot() {
    Set<String> requests = new HashSet<>();
    Set<String> responses = new HashSet<>();
    for (JsonNode operation : operations()) {
      if (operation.has("requestBody")) {
        operation
            .get("requestBody")
            .get("content")
            .valueStream()
            .forEach(media -> recordsReached(media.get("schema"), requests));
      }
      operation
          .get("responses")
          .valueStream()
          .flatMap(response -> response.path("content").valueStream())
          .forEach(media -> recordsReached(media.get("schema"), responses));
    }

    assertThat(requests)
        .containsExactlyInAnyOrderElementsOf(
            apiShapes.stream().filter(name -> name.endsWith("Request")).toList())
        .contains("PanelMemberRequest")
        .doesNotContainAnyElementsOf(responses)
        .allSatisfy(name ->
            assertThat(
                    schema(name).path("additionalProperties").isBoolean()
                        && !schema(name).get("additionalProperties").asBoolean())
                .as(name + " allows no other field")
                .isTrue());
    assertThat(responses)
        .contains("KumiteGameResponse", "ProblemDetail")
        .allSatisfy(name ->
            assertThat(schema(name).path("additionalProperties").isBoolean())
                .as(name + " says nothing of other fields")
                .isFalse());
    assertThat(
            schema("SettingsRequest")
                .get("properties")
                .get("pointValues")
                .get("additionalProperties")
                .get("type")
                .asString())
        .as("a map keeps the schema of its values")
        .isEqualTo("integer");
  }

  /** The walk below covers what the description says; this ties that to the real handlers. */
  @Test
  void requestBodies_areDescribedForEveryHandlerThatReadsOne() {
    Set<String> real =
        dev
            .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
            .getHandlerMethods()
            .entrySet()
            .stream()
            .filter(mapping ->
                FeatureWeb.holds(mapping.getValue().getBeanType().getPackageName())
                    && Arrays.stream(mapping.getValue().getMethodParameters())
                        .anyMatch(parameter -> parameter.hasParameterAnnotation(RequestBody.class)))
            .flatMap(mapping ->
                mapping.getKey().getMethodsCondition().getMethods().stream()
                    .flatMap(method ->
                        mapping.getKey().getPatternValues().stream()
                            .map(path -> method.name() + " " + path)))
            .collect(Collectors.toSet());

    assertThat(real).contains("POST " + ATHLETES, "PUT " + GAME + "/settings");
    assertThat(operationsWithBody().keySet()).containsExactlyInAnyOrderElementsOf(real);
  }

  /**
   * Every request body the description states, changed one way at a time from a body that passes,
   * proves the description and the boundary agree both ways: what the description refuses is
   * refused, and what it allows passes.
   *
   * <p>Each case changes one value of a body that answers anything but 400, so a 400 is for that
   * value. Refused: a field taken away, a field added at every level, a value of each JSON type the
   * schema does not take, and each stated bound broken, each answering a field error naming the
   * path; and a number past the range of its format, answering the framework's unreadable body.
   * Allowed: a field that is not required taken away, null where null is taken, and the edges of
   * the text, numbers, lists, map keys and enum values the schema takes. A 400 there from Jackson
   * or Bean Validation is a rule the server enforces and the description does not state. A 400 from
   * a rule the domain holds, across fields or against the game, carries no {@code errors} and
   * passes: the description does not state those.
   *
   * <p>A keyword this walk has no case for fails it, so a new one cannot go unproven, and a kind of
   * case that stops being made fails the count at the end.
   */
  @TestFactory
  Stream<DynamicTest> everyRequestBody_isRefusedOrPassed_asTheDescriptionSays() throws Exception {
    Map<String, String> passing = passingBodies();
    Map<String, String> bodies = operationsWithBody();
    List<DynamicTest> tests = new ArrayList<>();
    Set<String> kinds = new HashSet<>();

    assertThat(passing.keySet())
        .as("one passing body per request schema")
        .containsExactlyInAnyOrderElementsOf(Set.copyOf(bodies.values()));
    bodies.forEach((endpoint, record) -> {
      JsonNode body = JsonMapper.shared().readTree(passing.get(record));
      List<Change> changes = new ArrayList<>();
      changes.add(new Change("no body", "", null, Answer.UNREADABLE));
      changeRecord(schema(record), body, List.of(), changes);
      changes.forEach(change -> kinds.add(change.kind()));
      String method = endpoint.substring(0, endpoint.indexOf(' '));
      String path = endpoint.substring(endpoint.indexOf(' ') + 1).replaceAll("\\{[^}]+}", "none");
      tests.add(
          DynamicTest.dynamicTest(
              endpoint + ": the passing body",
              () ->
                  assertAnswered(
                      endpoint,
                      sendAsAdmin(method, path, body.toString()),
                      new Change("the passing body", "", body.toString(), Answer.PASSES))));
      changes.forEach(change ->
          tests.add(
              DynamicTest.dynamicTest(
                  endpoint + ": " + change.kind() + " " + change.at(),
                  () ->
                      assertAnswered(endpoint, sendAsAdmin(method, path, change.body()), change))));
    });

    assertThat(kinds)
        .as("every kind of case the description calls for today is made")
        .contains(
            "required",
            "unknown field",
            "maxLength",
            "minimum",
            "exclusiveMinimum",
            "pattern",
            "minItems",
            "minProperties",
            "enum",
            "enum by number",
            "enum by number as text",
            "propertyNames",
            "format int32",
            "format int64")
        .contains(TYPE_SAMPLES.keySet().stream().map(type -> "type " + type).toArray(String[]::new))
        .contains(
            "allows absent",
            "allows null",
            "allows empty text",
            "allows blank text",
            "allows the longest text",
            "allows zero",
            "allows a negative number",
            "allows a whole number",
            "allows true",
            "allows false",
            "allows an empty list",
            "allows a listed key",
            "allows a listed value");
    return tests.stream();
  }

  /** What the boundary answers a body: refused naming the path, refused unread, or let through. */
  private enum Answer {
    NAMED,
    UNREADABLE,
    PASSES
  }

  /** A body with one thing changed at a path, and the answer the description calls for. */
  private record Change(String kind, String at, @Nullable String body, Answer answer) {}

  /** A key of a map, which a path writes in brackets, as Bean Validation does. */
  private record Key(String name) {}

  private static Map<String, JsonNode> typeSamples() {
    JsonNodeFactory nodes = JsonNodeFactory.instance;
    Map<String, JsonNode> samples = new LinkedHashMap<>();
    samples.put("text", nodes.stringNode("1"));
    samples.put("whole number", nodes.numberNode(1));
    samples.put("fraction", nodes.numberNode(new BigDecimal("1.5")));
    samples.put("boolean", nodes.booleanNode(true));
    samples.put("list", nodes.arrayNode());
    samples.put("object", nodes.objectNode());
    samples.put("null", nodes.nullNode());
    return samples;
  }

  /** Whether a schema's types take a sample of {@link #TYPE_SAMPLES}. */
  private static boolean takes(Set<String> types, String sample) {
    return switch (sample) {
      case "text" -> types.contains("string");
      case "whole number" -> types.contains("integer") || types.contains("number");
      case "fraction" -> types.contains("number");
      case "boolean" -> types.contains("boolean");
      case "list" -> types.contains("array");
      case "object" -> types.contains("object");
      default -> types.contains("null");
    };
  }

  private static void changeRecord(
      JsonNode record, JsonNode passing, List<Object> at, List<Change> changes) {
    assertThat(record.propertyNames())
        .as("what a record schema says")
        .contains("properties")
        .isSubsetOf("type", "properties", "required", "additionalProperties", "description");
    if (!at.isEmpty()) {
      changeType(record, passing, at, changes);
    }
    changes.add(
        changed(
            "unknown field",
            passing,
            at,
            "notAField",
            object -> ((ObjectNode) object).put("notAField", 1)));
    List<String> required = namesOf(record.path("required"));
    record
        .get("properties")
        .properties()
        .forEach(property -> {
          String name = property.getKey();
          List<Object> field = append(at, name);
          assertThat(valueAt(passing, at).has(name))
              .as("the passing body holds " + pathOf(field))
              .isTrue();
          if (required.contains(name)) {
            changes.add(
                changed(
                    "required", passing, at, name, object -> ((ObjectNode) object).remove(name)));
          } else {
            changes.add(
                new Change(
                    "allows absent", pathOf(field), removed(passing, at, name), Answer.PASSES));
          }
          changeValue(property.getValue(), passing, field, changes);
        });
  }

  private static void changeValue(
      JsonNode value, JsonNode passing, List<Object> at, List<Change> changes) {
    JsonNodeFactory nodes = JsonNodeFactory.instance;
    value
        .properties()
        .forEach(keyword -> {
          JsonNode bound = keyword.getValue();
          switch (keyword.getKey()) {
            case "type" -> changeType(value, passing, at, changes);
            case "description" -> {
              // Says nothing a body can break.
            }
            case "format" -> {
              switch (bound.asString()) {
                case "int32" ->
                    changes.add(
                        unreadable(
                            "format int32",
                            passing,
                            at,
                            nodes.numberNode(
                                BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE))));
                case "int64" ->
                    changes.add(
                        unreadable(
                            "format int64",
                            passing,
                            at,
                            nodes.numberNode(
                                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE))));
                case "double", "password" -> {
                  // A double takes any number in its range; a password is any text.
                }
                default ->
                    throw new AssertionError(
                        "No case breaks format " + bound.asString() + " at " + pathOf(at));
              }
            }
            case "$ref" -> changeRecord(schema(refName(value)), passing, at, changes);
            case "anyOf", "oneOf" ->
                bound
                    .valueStream()
                    .forEach(choice -> {
                      if ("null".equals(choice.path("type").asString())) {
                        changes.add(allowed("allows null", passing, at, nodes.nullNode()));
                      } else {
                        changeValue(choice, passing, at, changes);
                      }
                    });
            case "items" -> changeValue(bound, passing, append(at, 0), changes);
            case "additionalProperties" ->
                changeValue(
                    bound,
                    passing,
                    append(at, new Key(valueAt(passing, at).propertyNames().iterator().next())),
                    changes);
            case "propertyNames" -> {
              assertThat(bound.propertyNames())
                  .as("what the keys of a map say")
                  .containsExactlyInAnyOrder("type", "enum");
              assertThat(namesOf(bound.get("enum"))).doesNotContain("NOT_A_KEY");
              JsonNode first = valueAt(passing, at).valueStream().findFirst().orElseThrow();
              changes.add(
                  replaced(
                      "propertyNames", passing, at, nodes.objectNode().set("NOT_A_KEY", first)));
              bound
                  .get("enum")
                  .valueStream()
                  .forEach(key ->
                      changes.add(
                          allowed(
                              "allows a listed key",
                              passing,
                              at,
                              nodes.objectNode().set(key.asString(), first))));
            }
            case "maxLength" -> {
              changes.add(
                  replaced(
                      "maxLength", passing, at, nodes.stringNode("a".repeat(bound.asInt() + 1))));
            }
            case "minLength" -> {
              if (bound.asInt() > 0) {
                changes.add(
                    replaced(
                        "minLength", passing, at, nodes.stringNode("a".repeat(bound.asInt() - 1))));
              }
            }
            case "minimum" ->
                changes.add(
                    replaced(
                        "minimum",
                        passing,
                        at,
                        nodes.numberNode(bound.decimalValue().subtract(BigDecimal.ONE))));
            case "exclusiveMinimum" ->
                changes.add(
                    replaced(
                        "exclusiveMinimum", passing, at, nodes.numberNode(bound.decimalValue())));
            case "maximum" ->
                changes.add(
                    replaced(
                        "maximum",
                        passing,
                        at,
                        nodes.numberNode(bound.decimalValue().add(BigDecimal.ONE))));
            case "exclusiveMaximum" ->
                changes.add(
                    replaced(
                        "exclusiveMaximum", passing, at, nodes.numberNode(bound.decimalValue())));
            case "pattern" -> {
              String outside =
                  Stream.of("!", " ", "")
                      .filter(text -> !Pattern.compile(bound.asString()).matcher(text).find())
                      .findFirst()
                      .orElseThrow(() -> new AssertionError("No text outside " + bound.asString()));
              changes.add(replaced("pattern", passing, at, nodes.stringNode(outside)));
            }
            case "minItems" -> {
              ArrayNode fewer = nodes.arrayNode();
              for (int i = 1; i < bound.asInt(); i++) {
                fewer.add(valueAt(passing, at).get(0));
              }
              changes.add(replaced("minItems", passing, at, fewer));
            }
            case "minProperties" -> {
              assertThat(bound.asInt()).as("an empty map breaks it").isEqualTo(1);
              changes.add(replaced("minProperties", passing, at, nodes.objectNode()));
            }
            case "enum" -> {
              assertThat(namesOf(bound)).doesNotContain("NOT_A_VALUE", "0");
              changes.add(replaced("enum", passing, at, nodes.stringNode("NOT_A_VALUE")));
              changes.add(replaced("enum by number", passing, at, nodes.numberNode(0)));
              changes.add(replaced("enum by number as text", passing, at, nodes.stringNode("0")));
              bound
                  .valueStream()
                  .filter(constant -> !constant.isNull())
                  .forEach(constant ->
                      changes.add(allowed("allows a listed value", passing, at, constant)));
            }
            default ->
                throw new AssertionError(
                    "No case breaks " + keyword.getKey() + " at " + pathOf(at) + ": add one");
          }
        });
  }

  /**
   * A value of each JSON type the schema does not take is refused, and the edges of the types it
   * takes pass, where no other keyword of the schema refuses them.
   */
  private static void changeType(
      JsonNode value, JsonNode passing, List<Object> at, List<Change> changes) {
    JsonNodeFactory nodes = JsonNodeFactory.instance;
    JsonNode type = value.get("type");
    Set<String> types = type.isArray() ? Set.copyOf(namesOf(type)) : Set.of(type.asString());
    for (Map.Entry<String, JsonNode> sample : TYPE_SAMPLES.entrySet()) {
      if (!takes(types, sample.getKey())) {
        changes.add(replaced("type " + sample.getKey(), passing, at, sample.getValue()));
      }
    }
    if (types.contains("null")) {
      changes.add(allowed("allows null", passing, at, nodes.nullNode()));
    }
    if (types.contains("string") && !value.has("enum")) {
      int longest = value.path("maxLength").asInt(1000);
      Map<String, String> texts = new LinkedHashMap<>();
      texts.put("allows empty text", "");
      texts.put("allows blank text", " ");
      texts.put("allows the longest text", "a".repeat(longest));
      texts.forEach((kind, text) -> {
        boolean taken =
            text.length() >= value.path("minLength").asInt(0)
                && text.length() <= longest
                && (!value.has("pattern")
                    || Pattern.compile(value.get("pattern").asString()).matcher(text).find());
        if (taken) {
          changes.add(allowed(kind, passing, at, nodes.stringNode(text)));
        }
      });
    }
    if (types.contains("integer") || types.contains("number")) {
      Map<String, BigDecimal> numbers = new LinkedHashMap<>();
      numbers.put("allows zero", BigDecimal.ZERO);
      numbers.put("allows a negative number", BigDecimal.ONE.negate());
      numbers.forEach((kind, number) -> {
        boolean taken =
            (!value.has("minimum") || number.compareTo(value.get("minimum").decimalValue()) >= 0)
                && (!value.has("exclusiveMinimum")
                    || number.compareTo(value.get("exclusiveMinimum").decimalValue()) > 0);
        if (taken) {
          changes.add(allowed(kind, passing, at, nodes.numberNode(number)));
        }
      });
    }
    if (types.contains("number")) {
      changes.add(allowed("allows a whole number", passing, at, nodes.numberNode(1)));
    }
    if (types.contains("boolean")) {
      changes.add(allowed("allows true", passing, at, nodes.booleanNode(true)));
      changes.add(allowed("allows false", passing, at, nodes.booleanNode(false)));
    }
    if (types.contains("array") && !value.has("minItems")) {
      changes.add(allowed("allows an empty list", passing, at, nodes.arrayNode()));
    }
    if (types.contains("object") && value.path("additionalProperties").isObject()) {
      JsonNode first = valueAt(passing, at).valueStream().findFirst().orElseThrow();
      if (!value.has("minProperties")) {
        changes.add(allowed("allows an empty map", passing, at, nodes.objectNode()));
      }
      if (!value.has("propertyNames")) {
        changes.add(
            allowed("allows any key", passing, at, nodes.objectNode().set("anyKey", first)));
      }
    }
  }

  /** The passing body with the object at {@code at} changed, answering a field error. */
  private static Change changed(
      String kind, JsonNode passing, List<Object> at, String name, Consumer<JsonNode> change) {
    JsonNode body = passing.deepCopy();
    change.accept(valueAt(body, at));
    return new Change(kind, pathOf(append(at, name)), body.toString(), Answer.NAMED);
  }

  private static String removed(JsonNode passing, List<Object> at, String name) {
    JsonNode body = passing.deepCopy();
    ((ObjectNode) valueAt(body, at)).remove(name);
    return body.toString();
  }

  private static Change replaced(String kind, JsonNode passing, List<Object> at, JsonNode value) {
    return new Change(kind, pathOf(at), replace(passing, at, value), Answer.NAMED);
  }

  private static Change unreadable(String kind, JsonNode passing, List<Object> at, JsonNode value) {
    return new Change(kind, pathOf(at), replace(passing, at, value), Answer.UNREADABLE);
  }

  private static Change allowed(String kind, JsonNode passing, List<Object> at, JsonNode value) {
    return new Change(kind, pathOf(at), replace(passing, at, value), Answer.PASSES);
  }

  private static String replace(JsonNode passing, List<Object> at, JsonNode value) {
    JsonNode body = passing.deepCopy();
    JsonNode parent = valueAt(body, at.subList(0, at.size() - 1));
    switch (at.getLast()) {
      case Integer index -> ((ArrayNode) parent).set(index, value);
      case Key key -> ((ObjectNode) parent).set(key.name(), value);
      default -> ((ObjectNode) parent).set((String) at.getLast(), value);
    }
    return body.toString();
  }

  private static JsonNode valueAt(JsonNode body, List<Object> at) {
    JsonNode value = body;
    for (Object step : at) {
      value =
          switch (step) {
            case Integer index -> value.get(index);
            case Key key -> value.get(key.name());
            default -> value.get((String) step);
          };
    }
    return value;
  }

  /** The path as Bean Validation writes it: {@code panel[0].role}, {@code pointValues[IPPON]}. */
  private static String pathOf(List<Object> at) {
    StringBuilder path = new StringBuilder();
    for (Object step : at) {
      switch (step) {
        case Integer index -> path.append('[').append(index).append(']');
        case Key key -> path.append('[').append(key.name()).append(']');
        default -> path.append(path.isEmpty() ? "" : ".").append(step);
      }
    }
    return path.toString();
  }

  private static List<Object> append(List<Object> at, Object step) {
    List<Object> longer = new ArrayList<>(at);
    longer.add(step);
    return longer;
  }

  private static void assertAnswered(String endpoint, HttpResponse<String> answer, Change change) {
    String label = endpoint + ", " + change.kind() + " " + change.at() + ": " + answer.body();
    if (change.answer() == Answer.PASSES) {
      assertThat(answer.statusCode()).as(label).isLessThan(500);
      if (answer.statusCode() == 400) {
        JsonNode problem = JsonMapper.shared().readTree(answer.body());
        assertThat(problem.has("errors") || problem.path("title").asString().equals("Bad Request"))
            .as(label + ": the boundary refused what the description takes")
            .isFalse();
      }
      return;
    }
    assertThat(answer.statusCode()).as(label).isEqualTo(400);
    assertThat(answer.headers().firstValue("Content-Type")).contains(PROBLEM_JSON);
    JsonNode problem = JsonMapper.shared().readTree(answer.body());
    if (change.answer() == Answer.UNREADABLE) {
      assertThat(problem.has("errors")).as(label).isFalse();
    } else {
      assertThat(problem.path("errors").propertyNames()).as(label).containsExactly(change.at());
    }
  }

  private static HttpResponse<String> sendAsAdmin(String method, String path, @Nullable String body)
      throws IOException, InterruptedException {
    return send(
        asAdmin(request(dev, path))
            .header("Content-Type", "application/json")
            .method(
                method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body)));
  }

  /** Each operation that takes a body, as {@code METHOD path}, and the record it takes. */
  private static Map<String, String> operationsWithBody() {
    Map<String, String> bodies = new TreeMap<>();
    description
        .get("paths")
        .properties()
        .forEach(path ->
            path.getValue()
                .properties()
                .forEach(operation -> {
                  JsonNode body = operation.getValue().path("requestBody");
                  if (!body.isMissingNode()) {
                    assertThat(body.get("required").asBoolean()).isTrue();
                    assertThat(body.get("content").propertyNames())
                        .containsExactly("application/json");
                    bodies.put(
                        operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey(),
                        refName(body.get("content").get("application/json").get("schema")));
                  }
                }));
    return bodies;
  }

  /**
   * A body per request record that passes the boundary: every field it describes, nothing broken,
   * and values that the rules checked before a read accept. A field it leaves out is sent as null.
   */
  private static Map<String, String> passingBodies() throws Exception {
    String aka = JsonMapper.shared().readTree(createAthlete().body()).get("id").asString();
    String ao = JsonMapper.shared().readTree(createAthlete().body()).get("id").asString();
    String referee =
        JsonMapper.shared()
            .readTree(sendAsAdmin("POST", REFEREES, "{\"name\": \"Sato\"}").body())
            .get("id")
            .asString();
    return Map.ofEntries(
        Map.entry("AthleteRequest", "{\"name\": \"Kenji\", \"weightKg\": 61.5}"),
        Map.entry("RefereeRequest", "{\"name\": \"Sato\"}"),
        Map.entry(
            "AccountRequest",
            """
            {"username": "walker", "password": "a-password", "role": "SCOREKEEPER"}
            """),
        Map.entry(
            "KumiteGameRequest",
            """
            {"format": "ELIMINATION", "medalGame": false, "extraGame": false,
             "akaAthleteId": "%s", "aoAthleteId": "%s",
             "panel": [{"refereeId": "%s", "role": "REFEREE"}]}
            """
                .formatted(aka, ao, referee)),
        Map.entry(
            "SettingsRequest",
            """
            {"durationMs": 90000, "durationsOfferedMs": [90000],
             "pointValues": {"IPPON": 3, "WAZA_ARI": 2, "YUKO": 1}, "leadToWin": 8,
             "finalWindowMs": 15000, "lateScoreWindowMs": 1500, "chuiLimit": 3,
             "roundRobinForfeitPoints": 4, "addTimeStepsMs": [10000],
             "medicalTimeMs": 180000, "timeShownToleranceMs": 1000, "expectedVersion": 0}
            """),
        Map.entry(
            "ScoreRequest",
            """
            {"id": "w1", "side": "AKA", "level": "YUKO", "source": "JUDGES", "exchange": 1,
             "clockRemainingMs": 1000}
            """),
        Map.entry(
            "PenaltyRequest",
            """
            {"id": "w1", "side": "AKA", "level": "CHUI", "infraction": "JOGAI", "exchange": 1,
             "clockRemainingMs": 1000}
            """),
        Map.entry(
            "ClockActionRequest",
            """
            {"id": "w1", "timer": "MAIN", "side": null, "action": "START", "timeShownMs": 1000,
             "amountMs": null, "pauseReason": null, "expectedVersion": null}
            """),
        Map.entry(
            "SenshuRecordRequest",
            """
            {"id": "w1", "side": "AKA", "kind": "AWARDED", "reason": null, "exchange": 1,
             "clockRemainingMs": 1000}
            """),
        Map.entry(
            "AssignmentRequest",
            """
            {"id": "w1", "kind": "ATHLETE", "side": "AKA", "athleteId": "%s", "refereeId": null,
             "role": null}
            """
                .formatted(aka)),
        Map.entry(
            "AmendmentRequest",
            """
            {"id": "w1", "kind": "CANCEL", "targetId": "s1", "reason": null}
            """),
        Map.entry(
            "ResultRequest",
            """
            {"id": "w1", "outcome": "WIN", "winner": "AKA", "reason": "POINTS",
             "reasonDetail": null, "bothDisqualified": false, "expectedVersion": 0}
            """),
        Map.entry(
            "StatusChangeRequest",
            """
            {"id": "w1", "kind": "APPROVE", "reason": null, "expectedVersion": 0}
            """));
  }

  /** The records a schema reaches through references, lists, maps and nullable choices. */
  private static void recordsReached(JsonNode schema, Set<String> into) {
    if (schema.has("$ref")) {
      String name = refName(schema);
      if (into.add(name)) {
        schema(name).path("properties").valueStream().forEach(field -> recordsReached(field, into));
      }
    }
    if (schema.path("items").isObject()) {
      recordsReached(schema.get("items"), into);
    }
    if (schema.path("additionalProperties").isObject()) {
      recordsReached(schema.get("additionalProperties"), into);
    }
    for (String choice : List.of("anyOf", "oneOf")) {
      schema.path(choice).valueStream().forEach(option -> recordsReached(option, into));
    }
  }

  private static String refName(JsonNode reference) {
    String ref = reference.get("$ref").asString();
    return ref.substring(ref.lastIndexOf('/') + 1);
  }

  private static Set<String> readApiShapes() {
    return new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.management")
            .stream()
            .filter(type -> FeatureWeb.holds(type.getPackageName()) && type.isRecord())
            .map(JavaClass::getSimpleName)
            .collect(Collectors.toSet());
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

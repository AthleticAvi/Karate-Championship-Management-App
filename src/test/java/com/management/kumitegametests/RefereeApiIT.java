package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

import com.management.referee.data.RefereeDocument;
import com.management.referee.data.RefereeDocumentMapper;
import com.management.referee.data.RefereeRepository;
import com.management.referee.domain.Referee;
import com.management.referee.domain.RefereeChangedException;
import com.management.referee.service.RefereeService;
import com.management.testsupport.IntegrationTestBase;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Referees on a real datastore, over real HTTP: each endpoint, who may call it, what is stored. */
class RefereeApiIT extends IntegrationTestBase {

  private static final String REFEREES = "/api/v1/referees";
  private static final String AUTHORIZATION = "Authorization";
  private static final String COLLECTION = "referee";

  @LocalServerPort private int port;

  @Autowired private Clock clock;

  @Autowired private RefereeRepository repository;

  private HttpRequest.Builder to(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  private static HttpRequest.Builder withName(
      HttpRequest.Builder request, String method, String name) {
    return request
        .header("Content-Type", "application/json")
        .method(method, BodyPublishers.ofString("{\"name\":\"" + name + "\"}"));
  }

  private HttpResponse<String> create(String name) throws Exception {
    return send(withName(to(REFEREES), "POST", name).header(AUTHORIZATION, adminCredentials()));
  }

  private String createdId(String name) throws Exception {
    return json(create(name)).get("id").asString();
  }

  private HttpResponse<String> rename(String id, String name, @Nullable String credentials)
      throws Exception {
    HttpRequest.Builder request = withName(to(REFEREES + "/" + id), "PUT", name);
    return send(credentials == null ? request : request.header(AUTHORIZATION, credentials));
  }

  private HttpResponse<String> delete(String id, @Nullable String credentials) throws Exception {
    HttpRequest.Builder request = to(REFEREES + "/" + id).DELETE();
    return send(credentials == null ? request : request.header(AUTHORIZATION, credentials));
  }

  private HttpResponse<String> read(String id) throws Exception {
    return send(to(REFEREES + "/" + id).GET());
  }

  private static JsonNode json(HttpResponse<String> response) {
    return JsonMapper.shared().readTree(response.body());
  }

  private Document storedReferee() {
    List<Document> stored = mongoTemplate.getCollection(COLLECTION).find().into(new ArrayList<>());
    assertThat(stored).hasSize(1);
    return stored.getFirst();
  }

  @Test
  void create_thenRead_answersTheSameReferee() throws Exception {
    HttpResponse<String> created = create("Sato");
    String id = json(created).get("id").asString();

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(created.headers().firstValue("Location")).contains(REFEREES + "/" + id);
    HttpResponse<String> read = read(id);
    assertThat(read.statusCode()).isEqualTo(200);
    assertThat(read.body()).isEqualTo(created.body());
  }

  @Test
  void rename_isWhatTheNextReadSees() throws Exception {
    String id = createdId("Sato");

    HttpResponse<String> renamed = rename(id, "Tanaka", adminCredentials());

    assertThat(renamed.statusCode()).isEqualTo(200);
    assertThat(json(renamed).get("name").asString()).isEqualTo("Tanaka");
    assertThat(json(read(id)).get("name").asString()).isEqualTo("Tanaka");
  }

  @Test
  void delete_removesTheReferee() throws Exception {
    String id = createdId("Sato");

    assertThat(delete(id, adminCredentials()).statusCode()).isEqualTo(204);

    assertThat(read(id).statusCode()).isEqualTo(404);
    assertThat(delete(id, adminCredentials()).statusCode()).isEqualTo(404);
    assertThat(rename(id, "Tanaka", adminCredentials()).statusCode()).isEqualTo(404);
  }

  /** The stored shape, read raw, so nothing the mapping layer does can hide a missing field. */
  @Test
  void storedReferee_carriesItsVersionsAndInstants() throws Exception {
    final Instant before = clock.instant();
    String id = createdId("Sato");
    Document created = storedReferee();
    rename(id, "Tanaka", adminCredentials());
    Document renamed = storedReferee();
    Instant after = clock.instant();

    assertThat(created.getString("name")).isEqualTo("Sato");
    assertThat(created.getInteger("schemaVersion")).isEqualTo(1);
    assertThat(created.get("createdAt")).isInstanceOf(Date.class);
    assertThat(created.get("modifiedAt")).isEqualTo(created.get("createdAt"));
    assertThat(created.getDate("createdAt").toInstant()).isBetween(before, after);
    assertThat(renamed.getString("name")).isEqualTo("Tanaka");
    assertThat(renamed.getLong("version")).isEqualTo(created.getLong("version") + 1);
    assertThat(renamed.get("createdAt")).isEqualTo(created.get("createdAt"));
    assertThat(renamed.getDate("modifiedAt").toInstant()).isBetween(before, after);
  }

  @Test
  void read_needsNoCredentials_andOtherCallsNeedThem() throws Exception {
    String id = createdId("Sato");

    assertThat(read(id).statusCode()).isEqualTo(200);
    assertThat(send(withName(to(REFEREES), "POST", "Suzuki")).statusCode()).isEqualTo(401);
    assertThat(rename(id, "Tanaka", null).statusCode()).isEqualTo(401);
    assertThat(delete(id, null).statusCode()).isEqualTo(401);
    assertThat(json(read(id)).get("name").asString()).isEqualTo("Sato");
  }

  @Test
  void scorekeeper_mayReadButNotChange() throws Exception {
    String id = createdId("Sato");
    String scorekeeper = basic(SCOREKEEPER, PASSWORD);

    assertThat(send(to(REFEREES + "/" + id).header(AUTHORIZATION, scorekeeper).GET()).statusCode())
        .isEqualTo(200);
    assertThat(
            send(withName(to(REFEREES), "POST", "Suzuki").header(AUTHORIZATION, scorekeeper))
                .statusCode())
        .isEqualTo(403);
    assertThat(rename(id, "Tanaka", scorekeeper).statusCode()).isEqualTo(403);
    assertThat(delete(id, scorekeeper).statusCode()).isEqualTo(403);
    assertThat(mongoTemplate.getCollection(COLLECTION).countDocuments()).isEqualTo(1);
    assertThat(json(read(id)).get("name").asString()).isEqualTo("Sato");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void blankName_isRefusedAndStoresNothing(String name) throws Exception {
    assertThat(create(name).statusCode()).isEqualTo(400);

    assertThat(mongoTemplate.collectionExists(COLLECTION)).isFalse();
  }

  /**
   * A real version race on the real datastore: another write lands between the rename's read and
   * its save. The loser hears what won, the save is not tried again, and the winner's name stays.
   */
  @Test
  void rename_thatLosesTheRace_answersTheWinnerAndIsNotRetried() throws Exception {
    String id = createdId("Sato");
    RefereeRepository racing = mock(RefereeRepository.class, delegatesTo(repository));
    willAnswer(
            call -> {
              RefereeDocument other = repository.findById(id).orElseThrow();
              repository.save(RefereeDocumentMapper.toDocument(new Referee(id, "Suzuki"), other));
              return repository.save(call.getArgument(0));
            })
        .given(racing)
        .save(any(RefereeDocument.class));

    assertThatThrownBy(() -> new RefereeService(racing, refereeId -> false).rename(id, "Tanaka"))
        .isInstanceOfSatisfying(
            RefereeChangedException.class,
            changed -> assertThat(changed.current()).isEqualTo(new Referee(id, "Suzuki")));
    then(racing).should().save(any(RefereeDocument.class));
    assertThat(json(read(id)).get("name").asString()).isEqualTo("Suzuki");
  }
}

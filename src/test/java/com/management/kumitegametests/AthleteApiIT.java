package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.testsupport.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Listing athletes on a real datastore, over HTTP. */
class AthleteApiIT extends IntegrationTestBase {

  private static final String ATHLETES = "/api/v1/athletes";

  @Autowired private TestRestTemplate anonymous;

  private void create(String name) {
    ResponseEntity<String> created =
        asAdmin().postForEntity(ATHLETES, new AthleteBody(name, 61.5), String.class);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
  }

  /** Read with no credentials: anyone may list athletes. */
  private JsonNode list(String query) {
    ResponseEntity<String> answer = anonymous.getForEntity(ATHLETES + query, String.class);
    assertThat(answer.getStatusCode().value()).isEqualTo(200);
    return JsonMapper.shared().readTree(answer.getBody());
  }

  private List<String> listed(String query) {
    return list(query).get("content").valueStream().map(a -> a.get("name").asString()).toList();
  }

  @Test
  void list_findsAnyPartOfTheName_inAnyCase_inNameOrder_paged() {
    for (String name : List.of("Kenji", "Haruto", "Ren", "Takeru")) {
      create(name);
    }

    assertThat(listed("")).containsExactly("Haruto", "Kenji", "Ren", "Takeru");
    assertThat(listed("?name=EN")).containsExactly("Kenji", "Ren");
    assertThat(listed("?name=.")).as("the name is matched as written").isEmpty();
    assertThat(listed("?page=1&size=3")).containsExactly("Takeru");
    assertThat(listed("?page=2147483647&size=100")).isEmpty();
    assertThat(list("?page=1&size=3").get("page").get("totalElements").asInt()).isEqualTo(4);
    assertThat(list("").get("content").get(0).get("weightKg").asDouble()).isEqualTo(61.5);
  }

  /** The body of a create, as a client sends it. */
  private record AthleteBody(String name, Double weightKg) {}
}

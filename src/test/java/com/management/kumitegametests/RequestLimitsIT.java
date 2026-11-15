package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.filter.FormContentFilter;
import org.springframework.web.multipart.MultipartResolver;
import tools.jackson.core.StreamReadConstraints;

/**
 * An oversized request is refused on the real server, whatever its shape.
 *
 * <p>The padded bodies are valid JSON, so the size is the only thing wrong with them.
 */
class RequestLimitsIT extends IntegrationTestBase {

  private static final String ATHLETES = "/api/v1/athletes";

  @Autowired private TestRestTemplate rest;
  @Autowired private ApplicationContext context;

  @Value("${spring.jackson.factory.constraints.read.max-document-length}")
  private int maxBody;

  @Test
  void post_withBodyUnderTheLimit_isAccepted() {
    ResponseEntity<String> response = post(padded(maxBody / 2));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  /** Padding fails in the parser, and a long value fails inside the deserialiser. */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void post_withBodyOverTheLimit_isRefusedAsProblemDetail(boolean padding) {
    String body = padding ? padded(maxBody * 2) : "{\"name\":\"" + "x".repeat(maxBody * 2) + "\"}";

    ResponseEntity<String> response = post(body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getBody())
        .contains("\"status\":413", "\"title\":\"Request too large\"", "\"detail\"");
  }

  @Test
  void post_withMalformedBody_staysA400() {
    assertThat(post("{not json").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /**
   * A smaller body would not cure this, so it is not a 413.
   *
   * <p>A name past the parser's limit fails while the name is read, before the body is bound, so
   * the field is never refused as unknown. A limit inside a value, such as nesting depth, cannot be
   * reached: an unknown field is refused at its name, and a known one at its first wrong token.
   */
  @Test
  void post_pastAnotherParserLimit_staysA400() {
    String name = "n".repeat(StreamReadConstraints.DEFAULT_MAX_NAME_LEN + 1);

    ResponseEntity<String> response = post("{\"" + name + "\":1,\"name\":\"Kata\"}");

    assertThat(name.length()).isLessThan(maxBody);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("\"title\":\"Bad Request\"").doesNotContain("errors");
  }

  /** Tomcat answers before Spring runs, so this is its own page and not a problem detail. */
  @Test
  void get_withHeadersOverTheLimit_isRefused() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Oversized", "x".repeat(16 * 1024));

    ResponseEntity<String> response =
        rest.exchange(ATHLETES, HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /** Both read a body outside the JSON limit, and a refused request looks the same either way. */
  @Test
  void bodyReadersNoEndpointNeeds_areOff() {
    assertThat(context.getBeanProvider(FormContentFilter.class).getIfAvailable()).isNull();
    assertThat(context.getBeanProvider(MultipartResolver.class).getIfAvailable()).isNull();
  }

  private ResponseEntity<String> post(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return asAdmin().postForEntity(ATHLETES, new HttpEntity<>(body, headers), String.class);
  }

  private static String padded(int length) {
    String json = "{\"name\":\"Kata\"}";
    return " ".repeat(length - json.length()) + json;
  }
}

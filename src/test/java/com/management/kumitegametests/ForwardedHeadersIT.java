package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.testsupport.IntegrationTestBase;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Forwarded headers count only from a proxy this deployment named.
 *
 * <p>The actuator index builds absolute links from the request, so it shows what the application
 * believes its public address is. Each test passes the settings a deployment would set in its
 * environment, as arguments.
 */
class ForwardedHeadersIT {

  private static final String PUBLIC_ADDRESS = "https://scores.example";
  private static final String LOCAL_ADDRESS = "http://localhost:";
  private static final String LOOPBACK = "127.0.0.0/8,::1/128";

  @Test
  void request_fromNamedProxy_buildsPublicUrls() throws Exception {
    try (ConfigurableApplicationContext context = start(trusting(LOOPBACK))) {
      assertThat(actuatorIndex(context)).contains(PUBLIC_ADDRESS + "/actuator");
    }
  }

  /** {@code Location} is relative, so it is right as long as it names no address of its own. */
  @Test
  void request_fromNamedProxy_buildsLocationThatResolvesToThePublicAddress() throws Exception {
    try (ConfigurableApplicationContext context = start(trusting(LOOPBACK))) {
      HttpRequest.Builder create =
          forwarded(context, "/api/v1/players")
              .header("Content-Type", "application/json")
              .POST(BodyPublishers.ofString("{\"name\":\"Kata\"}"));

      String location = send(create).headers().firstValue("Location").orElseThrow();

      assertThat(URI.create(PUBLIC_ADDRESS).resolve(location).toString())
          .startsWith(PUBLIC_ADDRESS + "/api/v1/players/");
    }
  }

  @Test
  void request_fromAnyoneElse_isIgnored() throws Exception {
    try (ConfigurableApplicationContext context = start(trusting("192.0.2.0/24"))) {
      assertThat(actuatorIndex(context)).contains(LOCAL_ADDRESS).doesNotContain(PUBLIC_ADDRESS);
    }
  }

  @Test
  void request_withNoProxyNamed_isIgnored() throws Exception {
    try (ConfigurableApplicationContext context =
        start(Map.of("FORWARD_HEADERS_STRATEGY", "native"))) {
      assertThat(actuatorIndex(context)).contains(LOCAL_ADDRESS).doesNotContain(PUBLIC_ADDRESS);
    }
  }

  @Test
  void request_underTheDefaults_isIgnored() throws Exception {
    try (ConfigurableApplicationContext context = start(Map.of())) {
      assertThat(actuatorIndex(context)).contains(LOCAL_ADDRESS).doesNotContain(PUBLIC_ADDRESS);
    }
  }

  private static Map<String, String> trusting(String proxies) {
    return Map.of("FORWARD_HEADERS_STRATEGY", "native", "TRUSTED_PROXIES", proxies);
  }

  private static ConfigurableApplicationContext start(Map<String, String> settings) {
    return IntegrationTestBase.startApplication(IntegrationTestBase.replicaSetUri(), settings);
  }

  private static String actuatorIndex(ConfigurableApplicationContext context)
      throws IOException, InterruptedException {
    return send(forwarded(context, "/actuator").GET()).body();
  }

  private static HttpRequest.Builder forwarded(
      ConfigurableApplicationContext context, String path) {
    String port = context.getEnvironment().getProperty("local.server.port");
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Forwarded-Proto", "https")
        .header("X-Forwarded-Host", URI.create(PUBLIC_ADDRESS).getHost())
        .header("X-Forwarded-For", "203.0.113.7");
  }

  private static HttpResponse<String> send(HttpRequest.Builder request)
      throws IOException, InterruptedException {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(request.build(), BodyHandlers.ofString());
    }
  }
}

package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.controllers.KumiteGameController;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import com.management.testsupport.WebSliceTestBase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The role table, proven through the real chain for every endpoint.
 *
 * <p><strong>The table is written out here on purpose.</strong> Reading it from the code under test
 * would prove the code agrees with itself. This is what the owner decided, and a wrong level in the
 * code fails against it. The first test compares it with the real mappings, so an endpoint added
 * without a row here fails the build.
 *
 * <p>Passing the chain is all that matters here: a request with no body answers whatever the
 * controller makes of that, so a permitted case asserts only that the refusals did not happen.
 * Credentials and the shape of a refusal are proven over HTTP in {@code SecurityIT}.
 */
@Import(SecurityRulesSliceTest.UnlistedController.class)
class SecurityRulesSliceTest extends WebSliceTestBase {

  private static final String PUBLIC = "PUBLIC";
  private static final String SCOREKEEPER = "SCOREKEEPER";
  private static final String ADMIN = "ADMIN";
  private static final String GAME = "/api/v1/kumitegame";
  private static final String ONE_GAME = GAME + "/{gameId}";
  private static final String PLAYERS = "/api/v1/players";
  private static final String ONE_PLAYER = PLAYERS + "/{playerId}";

  private static final Map<String, String> TABLE =
      Map.ofEntries(
          Map.entry("GET " + ONE_GAME, PUBLIC),
          Map.entry("GET " + ONE_PLAYER, PUBLIC),
          Map.entry("POST " + GAME, SCOREKEEPER),
          Map.entry("PUT " + ONE_GAME + "/add-point", SCOREKEEPER),
          Map.entry("PUT " + ONE_GAME + "/remove-point", SCOREKEEPER),
          Map.entry("PUT " + ONE_GAME + "/add-foul", SCOREKEEPER),
          Map.entry("PUT " + ONE_GAME + "/remove-foul", SCOREKEEPER),
          Map.entry("PUT " + ONE_GAME + "/kiken/{color}", SCOREKEEPER),
          Map.entry("PUT " + ONE_GAME + "/add-time", SCOREKEEPER),
          Map.entry("PUT " + ONE_GAME + "/disqualify/{color}", ADMIN),
          Map.entry("PUT " + ONE_GAME + "/override-winner", ADMIN),
          Map.entry("PUT " + ONE_GAME + "/update-winner/{color}", ADMIN),
          Map.entry("POST " + PLAYERS, ADMIN),
          Map.entry("DELETE " + ONE_PLAYER, ADMIN));

  @Autowired private MockMvc mockMvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @MockitoBean private KumiteGameService kumiteGameService;

  @MockitoBean private PlayerService playerService;

  private static Stream<String> endpointsAt(String... levels) {
    Set<String> wanted = Set.of(levels);
    return TABLE.entrySet().stream()
        .filter(row -> wanted.contains(row.getValue()))
        .map(Map.Entry::getKey);
  }

  private static Stream<String> publicEndpoints() {
    return endpointsAt(PUBLIC);
  }

  private static Stream<String> protectedEndpoints() {
    return endpointsAt(SCOREKEEPER, ADMIN);
  }

  private static Stream<String> scorekeeperEndpoints() {
    return endpointsAt(SCOREKEEPER);
  }

  private static Stream<String> adminEndpoints() {
    return endpointsAt(ADMIN);
  }

  /** Each row as a request, with every path variable filled in. */
  private static MockHttpServletRequestBuilder call(String endpoint) {
    List<String> parts = List.of(endpoint.split(" "));
    return request(HttpMethod.valueOf(parts.get(0)), parts.get(1).replaceAll("\\{[^}]+}", "RED"));
  }

  @Test
  void table_listsExactlyTheRealEndpoints() {
    Set<String> real =
        handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(mapping ->
                mapping
                    .getValue()
                    .getBeanType()
                    .getPackage()
                    .equals(KumiteGameController.class.getPackage()))
            .flatMap(mapping ->
                mapping.getKey().getMethodsCondition().getMethods().stream()
                    .flatMap(method ->
                        mapping.getKey().getPatternValues().stream()
                            .map(path -> method.name() + " " + path)))
            .collect(Collectors.toSet());

    assertThat(real).containsExactlyInAnyOrderElementsOf(TABLE.keySet());
  }

  @ParameterizedTest
  @MethodSource("protectedEndpoints")
  void noCredentials_isRefusedBeforeAnythingRuns(String endpoint) throws Exception {
    mockMvc.perform(call(endpoint)).andExpect(status().isUnauthorized());

    verifyNoInteractions(kumiteGameService, playerService);
  }

  @ParameterizedTest
  @MethodSource("publicEndpoints")
  void noCredentials_reachesPublicEndpoints(String endpoint) throws Exception {
    assertPermitted(call(endpoint));
  }

  @ParameterizedTest
  @MethodSource("adminEndpoints")
  void scorekeeper_isRefusedWhatOnlyAnAdminMayDo(String endpoint) throws Exception {
    mockMvc
        .perform(call(endpoint).with(user("someone").roles(SCOREKEEPER)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(kumiteGameService, playerService);
  }

  /** No stored role fails a Scorekeeper rule, so the wrong role here is one nothing admits. */
  @ParameterizedTest
  @MethodSource("protectedEndpoints")
  void anyOtherRole_isRefused(String endpoint) throws Exception {
    mockMvc
        .perform(call(endpoint).with(user("someone").roles("SPECTATOR")))
        .andExpect(status().isForbidden());

    verifyNoInteractions(kumiteGameService, playerService);
  }

  @ParameterizedTest
  @MethodSource("scorekeeperEndpoints")
  void scorekeeper_reachesItsEndpoints(String endpoint) throws Exception {
    assertPermitted(call(endpoint).with(user("someone").roles(SCOREKEEPER)));
  }

  @ParameterizedTest
  @MethodSource("protectedEndpoints")
  void admin_reachesEveryEndpoint(String endpoint) throws Exception {
    assertPermitted(call(endpoint).with(user("someone").roles(ADMIN)));
  }

  /** A real mapping with no rule, so deny by default is proven and not assumed. */
  @Test
  void endpointWithNoRule_isRefusedToEveryone() throws Exception {
    mockMvc.perform(get(UnlistedController.PATH)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(UnlistedController.PATH).with(user("someone").roles(ADMIN)))
        .andExpect(status().isForbidden());
  }

  private void assertPermitted(MockHttpServletRequestBuilder request) throws Exception {
    int status = mockMvc.perform(request).andReturn().getResponse().getStatus();

    assertThat(status).isNotIn(401, 403);
  }

  /** Not in the controllers package, so it stays out of the comparison with the table. */
  @RestController
  static class UnlistedController {

    static final String PATH = "/api/v1/unlisted";

    @GetMapping(PATH)
    String answer() {
      return "reached";
    }
  }
}

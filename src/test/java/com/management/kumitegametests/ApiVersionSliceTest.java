package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.testsupport.FeatureWeb;
import com.management.testsupport.WebSliceTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The API lives under one version prefix, and nowhere else.
 *
 * <p>The mocked beans match the controller slices, so all of them share one cached context.
 */
class ApiVersionSliceTest extends WebSliceTestBase {

  private static final String VERSIONED = "/api/v1/";

  @Autowired private MockMvc mockMvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  /** Read from the mappings, so an endpoint added later is covered without changing this test. */
  @Test
  void everyControllerEndpoint_isUnderTheVersionPrefix() {
    List<String> routes =
        handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(mapping -> FeatureWeb.holds(mapping.getValue().getBeanType().getPackageName()))
            .flatMap(mapping -> mapping.getKey().getPatternValues().stream())
            .toList();

    assertThat(routes).isNotEmpty().allMatch(route -> route.startsWith(VERSIONED));
  }

  @Test
  void oldPaths_forReads_areRefused() throws Exception {
    mockMvc.perform(get("/api/kumite-games/game-1")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/athletes/athlete-1")).andExpect(status().isUnauthorized());

    verifyNoInteractions(kumiteGameService(), athleteService());
  }

  @Test
  void oldPaths_forWrites_areRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/athletes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Kenji\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/kumite-games/game-1/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/athletes/athlete-1")).andExpect(status().isUnauthorized());

    verifyNoInteractions(kumiteGameService(), athleteService());
  }

  @Test
  void anotherVersion_isRefused() throws Exception {
    mockMvc.perform(get("/api/v2/athletes/athlete-1")).andExpect(status().isUnauthorized());

    verifyNoInteractions(kumiteGameService(), athleteService());
  }
}

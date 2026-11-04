package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.controllers.KumiteGameController;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import com.management.testsupport.WebSliceTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

  @MockitoBean private KumiteGameService kumiteGameService;

  @MockitoBean private PlayerService playerService;

  /** Read from the mappings, so an endpoint added later is covered without changing this test. */
  @Test
  void everyControllerEndpoint_isUnderTheVersionPrefix() {
    List<String> routes =
        handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(mapping ->
                mapping
                    .getValue()
                    .getBeanType()
                    .getPackage()
                    .equals(KumiteGameController.class.getPackage()))
            .flatMap(mapping -> mapping.getKey().getPatternValues().stream())
            .toList();

    assertThat(routes).isNotEmpty().allMatch(route -> route.startsWith(VERSIONED));
  }

  @Test
  void oldPaths_forReads_areNotFound() throws Exception {
    mockMvc.perform(get("/api/kumitegame/game-1")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/players/player-1")).andExpect(status().isNotFound());

    verifyNoInteractions(kumiteGameService, playerService);
  }

  @Test
  void oldPaths_forWrites_areNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Kenji\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(put("/api/kumitegame/game-1/add-foul").param("color", "RED"))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/players/player-1")).andExpect(status().isNotFound());

    verifyNoInteractions(kumiteGameService, playerService);
  }

  @Test
  void anotherVersion_isNotFound() throws Exception {
    mockMvc.perform(get("/api/v2/players/player-1")).andExpect(status().isNotFound());

    verifyNoInteractions(kumiteGameService, playerService);
  }
}

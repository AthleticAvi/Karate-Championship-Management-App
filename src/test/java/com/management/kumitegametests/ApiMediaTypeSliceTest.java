package com.management.kumitegametests;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.athlete.service.PlayerService;
import com.management.kumitegame.service.KumiteGameService;
import com.management.testsupport.PlayerBuilder;
import com.management.testsupport.WebSliceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The API speaks JSON only, though a YAML library is on the classpath for the API description.
 *
 * <p>The mocked beans match the controller slices, so all of them share one cached context.
 */
@WithMockUser(roles = "ADMIN")
class ApiMediaTypeSliceTest extends WebSliceTestBase {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KumiteGameService kumiteGameService;

  @MockitoBean private PlayerService playerService;

  @Test
  void yamlBody_isRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/players").contentType(MediaType.APPLICATION_YAML).content("name: Kenji"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

    verifyNoInteractions(playerService);
  }

  @Test
  void yamlAnswer_isRefused() throws Exception {
    given(playerService.getPlayer(anyString()))
        .willReturn(
            PlayerBuilder.newPlayer().alreadyPersistedAs("player-1").named("Kenji").build());

    mockMvc
        .perform(get("/api/v1/players/player-1").accept(MediaType.APPLICATION_YAML))
        .andExpect(status().isNotAcceptable());
  }
}

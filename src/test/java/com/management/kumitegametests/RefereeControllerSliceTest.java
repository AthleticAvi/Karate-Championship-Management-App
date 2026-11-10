package com.management.kumitegametests;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.athlete.service.PlayerService;
import com.management.kumitegame.service.KumiteGameService;
import com.management.referee.domain.Referee;
import com.management.referee.domain.RefereeChangedException;
import com.management.referee.domain.RefereeNotFoundException;
import com.management.testsupport.WebSliceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every endpoint on {@code /api/v1/referees}, at the HTTP boundary. Who may call each is proven in
 * {@code SecurityRulesSliceTest} and {@code RefereeApiIT}.
 *
 * <p>The mocked beans match the other controller slices, so all of them share one cached context.
 */
@WithMockUser(roles = "ADMIN")
class RefereeControllerSliceTest extends WebSliceTestBase {

  private static final String REFEREES = "/api/v1/referees";
  private static final String ONE_REFEREE = REFEREES + "/{refereeId}";
  private static final String SATO =
      """
      { "id": "referee-1", "name": "Sato" }
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KumiteGameService kumiteGameService;

  @MockitoBean private PlayerService playerService;

  @Test
  void create_returns201WithLocationAndExactlyTheReferee() throws Exception {
    given(refereeService().create("Sato")).willReturn(new Referee("referee-1", "Sato"));

    mockMvc
        .perform(
            post(REFEREES).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Sato\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", REFEREES + "/referee-1"))
        .andExpect(content().json(SATO, JsonCompareMode.STRICT));
  }

  @Test
  void create_whenTheNameIsBlank_returns400NamingTheField() throws Exception {
    mockMvc
        .perform(
            post(REFEREES).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors.name").value("a referee needs a name"));

    then(refereeService()).shouldHaveNoInteractions();
  }

  @Test
  void get_returnsExactlyTheReferee() throws Exception {
    given(refereeService().get("referee-1")).willReturn(new Referee("referee-1", "Sato"));

    mockMvc
        .perform(get(ONE_REFEREE, "referee-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(SATO, JsonCompareMode.STRICT));
  }

  @Test
  void get_whenMissing_returns404AsProblemDetail() throws Exception {
    given(refereeService().get(anyString()))
        .willThrow(new RefereeNotFoundException("No referee has the id nope"));

    mockMvc
        .perform(get(ONE_REFEREE, "nope"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Referee not found"));
  }

  @Test
  void rename_returnsTheRenamedReferee() throws Exception {
    given(refereeService().rename("referee-1", "Tanaka"))
        .willReturn(new Referee("referee-1", "Tanaka"));

    mockMvc
        .perform(
            put(ONE_REFEREE, "referee-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Tanaka\"}"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    { "id": "referee-1", "name": "Tanaka" }
                    """,
                    JsonCompareMode.STRICT));
  }

  /** The current referee travels in the problem detail, so the caller needs no second read. */
  @Test
  void rename_whenAnotherWriteWon_returns409WithTheCurrentReferee() throws Exception {
    given(refereeService().rename("referee-1", "Tanaka"))
        .willThrow(
            new RefereeChangedException(
                new Referee("referee-1", "Suzuki"), new OptimisticLockingFailureException("lost")));

    mockMvc
        .perform(
            put(ONE_REFEREE, "referee-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Tanaka\"}"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Concurrent update"))
        .andExpect(jsonPath("$.current.id").value("referee-1"))
        .andExpect(jsonPath("$.current.name").value("Suzuki"));
  }

  @Test
  void rename_whenTheNameIsBlank_returns400() throws Exception {
    mockMvc
        .perform(
            put(ONE_REFEREE, "referee-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.name").exists());

    then(refereeService()).shouldHaveNoInteractions();
  }

  @Test
  void delete_returns204WithNoBody() throws Exception {
    mockMvc
        .perform(delete(ONE_REFEREE, "referee-1"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    then(refereeService()).should().delete("referee-1");
  }

  @Test
  void delete_whenMissing_returns404() throws Exception {
    willThrow(new RefereeNotFoundException("No referee has the id nope"))
        .given(refereeService())
        .delete("nope");

    mockMvc
        .perform(delete(ONE_REFEREE, "nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}

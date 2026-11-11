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

import com.management.athlete.domain.Athlete;
import com.management.athlete.domain.AthleteChangedException;
import com.management.athlete.domain.AthleteInUseException;
import com.management.athlete.domain.AthleteNotFoundException;
import com.management.testsupport.WebSliceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every endpoint on {@code /api/v1/athletes}, at the HTTP boundary. Who may call each is proven in
 * {@code SecurityRulesSliceTest}.
 */
@WithMockUser(roles = "ADMIN")
class AthleteControllerSliceTest extends WebSliceTestBase {

  private static final String ATHLETES = "/api/v1/athletes";
  private static final String ONE_ATHLETE = ATHLETES + "/{athleteId}";
  private static final String KENJI =
      """
      { "id": "athlete-1", "name": "Kenji", "weightKg": 61.5 }
      """;

  @Autowired private MockMvc mockMvc;

  @Test
  void create_returns201WithLocationAndExactlyTheAthlete() throws Exception {
    given(athleteService().create("Kenji", 61.5))
        .willReturn(new Athlete("athlete-1", "Kenji", 61.5));

    mockMvc
        .perform(
            post(ATHLETES)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Kenji\", \"weightKg\": 61.5}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", ATHLETES + "/athlete-1"))
        .andExpect(content().json(KENJI, JsonCompareMode.STRICT));
  }

  @Test
  void create_withoutWeight_answersNull() throws Exception {
    given(athleteService().create("Kenji", null))
        .willReturn(new Athlete("athlete-1", "Kenji", null));

    mockMvc
        .perform(
            post(ATHLETES).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Kenji\"}"))
        .andExpect(status().isCreated())
        .andExpect(
            content()
                .json(
                    "{\"id\": \"athlete-1\", \"name\": \"Kenji\", \"weightKg\": null}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void create_withWeightOfZero_isRefused() throws Exception {
    mockMvc
        .perform(
            post(ATHLETES)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Kenji\", \"weightKg\": 0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.weightKg").exists());

    then(athleteService()).shouldHaveNoInteractions();
  }

  @Test
  void get_whenMissing_returns404AsProblemDetail() throws Exception {
    given(athleteService().get(anyString()))
        .willThrow(new AthleteNotFoundException("No athlete has the id nope"));

    mockMvc
        .perform(get(ONE_ATHLETE, "nope"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Athlete not found"));
  }

  @Test
  void update_whenAnotherWriteWon_returns409WithTheCurrentAthlete() throws Exception {
    given(athleteService().update("athlete-1", "Ken", 61.5))
        .willThrow(
            new AthleteChangedException(
                new Athlete("athlete-1", "Kenta", 61.5),
                new OptimisticLockingFailureException("lost")));

    mockMvc
        .perform(
            put(ONE_ATHLETE, "athlete-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Ken\", \"weightKg\": 61.5}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.current.name").value("Kenta"));
  }

  @Test
  void delete_whileGamesNameTheAthlete_isConflict() throws Exception {
    willThrow(new AthleteInUseException("A game refers to athlete athlete-1."))
        .given(athleteService())
        .delete("athlete-1");

    mockMvc
        .perform(delete(ONE_ATHLETE, "athlete-1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Athlete in use"));
  }

  @Test
  void delete_returns204WithNoBody() throws Exception {
    mockMvc
        .perform(delete(ONE_ATHLETE, "athlete-1"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }
}

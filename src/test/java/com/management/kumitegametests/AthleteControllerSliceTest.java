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
import com.management.domain.IdentityChangedException;
import com.management.testsupport.WebSliceTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

  /** The page and its size come from the caller, and the answer says where it sits among all. */
  @Test
  void list_returnsThePage_withWhereItSits() throws Exception {
    given(athleteService().list("sa", PageRequest.of(1, 5)))
        .willReturn(
            new PageImpl<>(
                List.of(new Athlete("athlete-1", "Kenji", 61.5)), PageRequest.of(1, 5), 6));

    mockMvc
        .perform(get(ATHLETES).param("name", "sa").param("page", "1").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "content": [{ "id": "athlete-1", "name": "Kenji", "weightKg": 61.5 }],
                      "page": {"size": 5, "number": 1, "totalElements": 6, "totalPages": 2}
                    }
                    """,
                    JsonCompareMode.STRICT));
  }

  @Test
  void list_withNothingAsked_searchesEveryName_onTheFirstPageOf20() throws Exception {
    given(athleteService().list("", PageRequest.of(0, 20)))
        .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc.perform(get(ATHLETES)).andExpect(status().isOk());

    then(athleteService()).should().list("", PageRequest.of(0, 20));
  }

  /** A size past the configured most is cut to it, not refused. */
  @Test
  void list_withSizePastTheMost_asksFor100() throws Exception {
    given(athleteService().list("", PageRequest.of(0, 100)))
        .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

    mockMvc.perform(get(ATHLETES).param("size", "5000")).andExpect(status().isOk());

    then(athleteService()).should().list("", PageRequest.of(0, 100));
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

  /** A kind the handler has no response shape for is a server fault, and nothing of it is sent. */
  @Test
  void update_whenTheChangedKindHasNoResponseShape_isServerError() throws Exception {
    given(athleteService().update("athlete-1", "Ken", 61.5))
        .willThrow(
            new IdentityChangedException(
                "club",
                "Club club-1 was changed",
                "club-1",
                new OptimisticLockingFailureException("lost")) {});

    mockMvc
        .perform(
            put(ONE_ATHLETE, "athlete-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Ken\", \"weightKg\": 61.5}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.current").doesNotExist());
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

package com.management.kumitegametests;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.testsupport.WebSliceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A failed request carries its ID, so a bug report can be tied to the logs.
 *
 * <p>The mocked beans match the controller slices, so all of them share one cached context.
 */
@WithMockUser(roles = "ADMIN")
class RequestCorrelationSliceTest extends WebSliceTestBase {

  private static final String HEADER = "X-Correlation-ID";
  private static final String ID = "bug-report-1";

  @Autowired private MockMvc mockMvc;

  @Test
  void unexpectedFailure_stillCarriesTheId() throws Exception {
    given(athleteService().get(anyString())).willThrow(new IllegalStateException("broken"));

    mockMvc
        .perform(get("/api/v1/athletes/athlete-1").header(HEADER, ID))
        .andExpect(status().isInternalServerError())
        .andExpect(header().string(HEADER, ID));
  }

  @Test
  void rejectedRequest_stillCarriesTheId() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/athletes")
                .header(HEADER, ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string(HEADER, ID));
  }
}

package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.management.dto.AccountRequest;
import com.management.enums.AccountRole;
import com.management.exceptions.AccountAlreadyExistsException;
import com.management.exceptions.AccountNotFoundException;
import com.management.exceptions.LastAdminException;
import com.management.models.Account;
import com.management.models.NewAccount;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import com.management.testsupport.WebSliceTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The account API's wire format and failures, through the real chain as an Admin. */
@WithMockUser(roles = "ADMIN")
class AccountControllerSliceTest extends WebSliceTestBase {

  private static final String ACCOUNTS = "/api/v1/accounts";
  private static final String PROBLEM = "application/problem+json";
  private static final String PASSWORD = "not-a-real-one";

  @Autowired private MockMvc mockMvc;

  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private KumiteGameService kumiteGameService;

  @MockitoBean private PlayerService playerService;

  private ResultActions create(String body) throws Exception {
    return mockMvc.perform(post(ACCOUNTS).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static String body(String username, String password, String role) {
    return "{\"username\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}"
        .formatted(username, password, role);
  }

  private void givenCreateAnswers() {
    given(accountService().create(any(NewAccount.class)))
        .willAnswer(call -> {
          NewAccount account = call.getArgument(0);
          return new Account(account.username(), account.passwordHash(), account.role());
        });
  }

  /** The whole body, so a field added to the response fails here. */
  @Test
  void create_answersUsernameAndRoleOnly() throws Exception {
    givenCreateAnswers();

    create(body("mat.1_a-b", PASSWORD, "SCOREKEEPER"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", ACCOUNTS + "/mat.1_a-b"))
        .andExpect(content().json("{\"username\":\"mat.1_a-b\",\"role\":\"SCOREKEEPER\"}", true));
  }

  @Test
  void create_handsTheServiceOnlyTheHash() throws Exception {
    givenCreateAnswers();

    create(body("admin2", PASSWORD, "ADMIN")).andExpect(status().isCreated());

    ArgumentCaptor<NewAccount> stored = ArgumentCaptor.forClass(NewAccount.class);
    verify(accountService()).create(stored.capture());
    assertThat(stored.getValue().role()).isEqualTo(AccountRole.ADMIN);
    assertThat(stored.getValue().passwordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    assertThat(passwordEncoder.matches(PASSWORD, stored.getValue().passwordHash())).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "with:colon", "with space", "with/slash"})
  void create_withUnusableUsername_isRefused(String username) throws Exception {
    create(body(username, PASSWORD, "ADMIN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.username").exists());

    verifyNoInteractions(accountService());
  }

  @Test
  void create_withLongUsername_isRefused() throws Exception {
    create(body("x".repeat(65), PASSWORD, "ADMIN")).andExpect(status().isBadRequest());
  }

  @Test
  void create_withBlankPassword_isRefused() throws Exception {
    create(body("mat1", " ", "ADMIN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.password").exists());
  }

  @Test
  void create_withLongPassword_isRefusedWithoutRepeatingIt() throws Exception {
    String tooLong = "s3cret".repeat(13);

    String response =
        create(body("mat1", tooLong, "ADMIN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(response).doesNotContain(tooLong);
  }

  /** Within the character limit and past the hash's limit in bytes. */
  @Test
  void create_withPasswordTooLongInBytes_isRefused() throws Exception {
    create(body("mat1", "é".repeat(60), "ADMIN")).andExpect(status().isBadRequest());

    verifyNoInteractions(accountService());
  }

  @Test
  void create_withNoRole_isRefused() throws Exception {
    create("{\"username\":\"mat1\",\"password\":\"x\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.role").exists());
  }

  @Test
  void create_withUnknownRole_isRefused() throws Exception {
    create(body("mat1", PASSWORD, "REFEREE")).andExpect(status().isBadRequest());
  }

  @Test
  void create_ofExistingUsername_isConflict() throws Exception {
    given(accountService().create(any(NewAccount.class)))
        .willThrow(
            new AccountAlreadyExistsException("An account already has that username.", null));

    create(body("mat1", PASSWORD, "ADMIN"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(PROBLEM))
        .andExpect(jsonPath("$.detail").value("An account already has that username."));
  }

  @Test
  void delete_answersNoContent() throws Exception {
    mockMvc
        .perform(delete(ACCOUNTS + "/mat.1"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    verify(accountService()).delete("mat.1");
  }

  @Test
  void delete_ofNobody_isNotFound() throws Exception {
    willThrow(new AccountNotFoundException("No account has that username."))
        .given(accountService())
        .delete("none");

    mockMvc
        .perform(delete(ACCOUNTS + "/none"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(PROBLEM));
  }

  @Test
  void delete_ofLastAdmin_isConflict() throws Exception {
    willThrow(new LastAdminException("The last Admin cannot be removed."))
        .given(accountService())
        .delete("admin");

    mockMvc
        .perform(delete(ACCOUNTS + "/admin"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("The last Admin cannot be removed."));
  }

  /** A write conflict that outlasted every retry, in the form the mapping layer gives it. */
  @Test
  void delete_thatKeepsLosingWriteConflicts_isConflictAndSaysNothingInternal() throws Exception {
    willThrow(new DataIntegrityViolationException("Write conflict on kumitedb.account"))
        .given(accountService())
        .delete("admin");

    String response =
        mockMvc
            .perform(delete(ACCOUNTS + "/admin"))
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(response).doesNotContain("kumitedb");
  }

  @Test
  void request_neverPrintsItsPassword() {
    assertThat(new AccountRequest("mat1", PASSWORD, AccountRole.ADMIN).toString())
        .contains("mat1")
        .doesNotContain(PASSWORD);
    assertThat(new NewAccount("mat1", "the-hash", AccountRole.ADMIN).toString())
        .contains("mat1")
        .doesNotContain("the-hash");
  }
}

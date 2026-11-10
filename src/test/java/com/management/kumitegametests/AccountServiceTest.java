package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.management.account.data.AccountRepository;
import com.management.account.domain.Account;
import com.management.account.domain.AccountRole;
import com.management.account.service.AccountRemoval;
import com.management.account.service.AccountService;
import com.management.config.FirstAdminProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class AccountServiceTest {

  private final AccountRepository repository = mock(AccountRepository.class);
  private final AccountService service = new AccountService(repository, mock(AccountRemoval.class));

  /**
   * Two instances starting together: the loser's insert fails, and an Admin exists all the same.
   */
  @Test
  void firstAdmin_whenAnotherInstanceGotThereFirst_isFine() {
    given(repository.insert(any(Account.class))).willThrow(new DuplicateKeyException("taken"));
    given(repository.existsByRole(AccountRole.ADMIN)).willReturn(true);

    assertThatCode(() -> service.createFirstAdmin("admin", "hash")).doesNotThrowAnyException();
  }

  @Test
  void firstAdmin_whenUsernameIsHeldByAnotherRole_refuses() {
    given(repository.insert(any(Account.class))).willThrow(new DuplicateKeyException("taken"));
    given(repository.existsByRole(AccountRole.ADMIN)).willReturn(false);

    assertThatThrownBy(() -> service.createFirstAdmin("admin", "hash"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_USERNAME");
  }

  @Test
  void account_neverPrintsItsHash() {
    assertThat(new Account("admin", "the-hash", AccountRole.ADMIN).toString())
        .contains("admin")
        .doesNotContain("the-hash");
  }

  @Test
  void firstAdminSettings_neverPrintThePassword() {
    assertThat(new FirstAdminProperties("admin", "the-password", true).toString())
        .contains("admin")
        .doesNotContain("the-password");
  }
}

package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.management.account.data.AccountRepository;
import com.management.account.domain.LastAdminException;
import com.management.account.service.AccountRemoval;
import com.management.account.service.AccountService;
import com.mongodb.MongoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * The retry around deleting an account, through its real proxy.
 *
 * <p>A real run cannot force a conflict every time, and this is what proves the handler's 409: what
 * leaves the proxy after the last attempt is the failure itself.
 */
@SpringJUnitConfig(AccountDeleteRetryTest.Wiring.class)
class AccountDeleteRetryTest {

  private static final int ATTEMPTS = 10;

  @Autowired private AccountService service;

  @Autowired private AccountRemoval removal;

  @BeforeEach
  void forgetEarlierCalls() {
    reset(removal);
  }

  private static DataIntegrityViolationException writeConflict() {
    MongoException conflict = new MongoException("WriteConflict");
    conflict.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
    return new DataIntegrityViolationException("Write conflict", conflict);
  }

  @Test
  void writeConflict_isRetriedUntilItClears() {
    willThrow(writeConflict()).willDoNothing().given(removal).remove("admin");

    assertThatCode(() -> service.delete("admin")).doesNotThrowAnyException();

    verify(removal, times(2)).remove("admin");
  }

  @Test
  void writeConflict_thatNeverClears_leavesAsItself() {
    willThrow(writeConflict()).given(removal).remove("admin");

    assertThatThrownBy(() -> service.delete("admin"))
        .isInstanceOf(DataIntegrityViolationException.class);

    verify(removal, times(ATTEMPTS)).remove("admin");
  }

  @Test
  void refusal_isNotRetried() {
    willThrow(new LastAdminException("refused")).given(removal).remove("admin");

    assertThatThrownBy(() -> service.delete("admin")).isInstanceOf(LastAdminException.class);

    verify(removal).remove("admin");
  }

  @Test
  void success_runsOnce() {
    willDoNothing().given(removal).remove("admin");

    service.delete("admin");

    verify(removal).remove("admin");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableResilientMethods
  static class Wiring {

    @Bean
    AccountRemoval accountRemoval() {
      return mock(AccountRemoval.class);
    }

    @Bean
    AccountService accountService(AccountRemoval removal) {
      return new AccountService(mock(AccountRepository.class), removal);
    }
  }
}

package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.referee.data.RefereeDocument;
import com.management.referee.data.RefereeRepository;
import com.management.referee.service.RefereeService;
import com.management.testsupport.IntegrationTestBase;
import com.management.testsupport.TestClock;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The created and modified instants come from the clock bean, through Spring Data auditing.
 *
 * <p>A context of its own, because only a replaced clock bean shows where the time comes from: on
 * the shared context the bean ticks with the system clock, and the two cannot be told apart.
 */
@Import(RefereeAuditingIT.SetClock.class)
class RefereeAuditingIT extends IntegrationTestBase {

  private static final Instant LATER = TestClock.NOW.plusSeconds(90);

  @Autowired private RefereeService referees;

  @Autowired private RefereeRepository repository;

  @Autowired private SettableClock clock;

  @BeforeEach
  void startAtTheTestTime() {
    clock.set(TestClock.NOW);
  }

  @Test
  void create_storesTheClockTimeAsCreatedAndModified() {
    String id = referees.create("Sato").id();

    RefereeDocument stored = repository.findById(id).orElseThrow();
    assertThat(stored.getCreatedAt()).isEqualTo(TestClock.NOW);
    assertThat(stored.getModifiedAt()).isEqualTo(TestClock.NOW);
    assertThat(stored.getSchemaVersion()).isEqualTo(1);
    assertThat(stored.getVersion()).isEqualTo(1);
  }

  @Test
  void rename_movesOnlyTheModifiedTimeAndTheVersion() {
    String id = referees.create("Sato").id();
    clock.set(LATER);

    referees.rename(id, "Tanaka");

    RefereeDocument stored = repository.findById(id).orElseThrow();
    assertThat(stored.getCreatedAt()).isEqualTo(TestClock.NOW);
    assertThat(stored.getModifiedAt()).isEqualTo(LATER);
    assertThat(stored.getVersion()).isEqualTo(2);
  }

  /** Replaces the clock bean for this context only. */
  @TestConfiguration(proxyBeanMethods = false)
  static class SetClock {

    @Bean
    @Primary
    SettableClock settableClock() {
      return new SettableClock();
    }
  }

  /** A clock a test moves by hand. UTC, like the real bean. */
  static final class SettableClock extends Clock {

    private volatile Instant now = TestClock.NOW;

    void set(Instant instant) {
      now = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException("this clock is UTC only");
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}

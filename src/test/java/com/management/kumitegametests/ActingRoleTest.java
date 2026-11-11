package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.account.domain.AccountRole;
import com.management.kumitegame.domain.ActingRole;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The game finds the acting role by the account role's name, and its feature may not import the
 * account's type, so only this test ties the two.
 */
class ActingRoleTest {

  @Test
  void actingRoles_areNamedAsTheAccountRoles() {
    assertThat(Arrays.stream(ActingRole.values()).map(Enum::name))
        .containsExactlyElementsOf(Arrays.stream(AccountRole.values()).map(Enum::name).toList());
  }
}

package com.management.security;

import com.management.config.DatastoreAccepted;
import com.management.config.FirstAdminProperties;
import com.management.services.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first Admin from the environment, once the datastore has been accepted.
 *
 * <p>Waits for {@link DatastoreAccepted} rather than running at startup, so nothing is written to a
 * datastore about to be refused, and a datastore that first answers after startup gets the same
 * check. A datastore that answers but cannot be read, such as one refusing the login, is an outage:
 * the application stays up with health DOWN, as it does without this class, and a restart checks
 * again.
 */
@Component
class FirstAdminSetup {

  private static final Logger log = LoggerFactory.getLogger(FirstAdminSetup.class);
  private static final String NOT_SET =
      "No Admin exists, and ADMIN_USERNAME and ADMIN_PASSWORD are not both set.";

  private final AccountService accounts;
  private final PasswordEncoder passwordEncoder;
  private final FirstAdminProperties properties;

  FirstAdminSetup(
      AccountService accounts, PasswordEncoder passwordEncoder, FirstAdminProperties properties) {
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
  }

  @EventListener(DatastoreAccepted.class)
  void createIfNoAdmin() {
    try {
      if (!accounts.hasAdmin()) {
        createFromSettings();
      }
    } catch (DataAccessException e) {
      log.warn("The datastore could not be read, so the first Admin was not checked");
    }
  }

  private void createFromSettings() {
    if (StringUtils.hasText(properties.username()) && StringUtils.hasText(properties.password())) {
      accounts.createFirstAdmin(properties.username(), hash(properties.password()));
      log.info("Created the first Admin");
    } else if (properties.required()) {
      throw new IllegalStateException(NOT_SET);
    } else {
      log.warn("{} Nobody can sign in until an Admin exists.", NOT_SET);
    }
  }

  private String hash(String password) {
    try {
      return passwordEncoder.encode(password);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("ADMIN_PASSWORD cannot be hashed: it is too long.", e);
    }
  }
}

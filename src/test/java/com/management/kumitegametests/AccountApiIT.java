package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.management.account.data.AccountRepository;
import com.management.account.domain.Account;
import com.management.account.domain.AccountRole;
import com.management.account.domain.LastAdminException;
import com.management.account.service.AccountService;
import com.management.athlete.data.AthleteDocumentMapper;
import com.management.athlete.data.AthleteRepository;
import com.management.testsupport.IntegrationTestBase;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Accounts on a real datastore: what is stored, who can sign in, and the last Admin. */
class AccountApiIT extends IntegrationTestBase {

  private static final String ACCOUNTS = "/api/v1/accounts";
  private static final String AUTHORIZATION = "Authorization";
  private static final String NEW_PASSWORD = "n3w-acc0unt-password";
  private static final String GAMES = "/api/v1/kumite-games";
  private static final int ROUNDS = 25;

  @LocalServerPort private int port;

  @Autowired private AccountRepository accounts;

  @Autowired private AccountService accountService;

  @Autowired private AthleteRepository athletes;

  private HttpRequest.Builder to(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  private HttpRequest.Builder post(String path, String body) {
    return to(path).header("Content-Type", "application/json").POST(BodyPublishers.ofString(body));
  }

  private HttpResponse<String> createAccount(String username, String password, String role)
      throws Exception {
    return send(
        post(
                ACCOUNTS,
                "{\"username\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}"
                    .formatted(username, password, role))
            .header(AUTHORIZATION, adminCredentials()));
  }

  private HttpResponse<String> deleteAccount(String username, String credentials) throws Exception {
    return send(to(ACCOUNTS + "/" + username).header(AUTHORIZATION, credentials).DELETE());
  }

  /** Creating a game is a Scorekeeper action, so it shows whether the account may act. */
  private int statusOfNewGame(String username, String password) throws Exception {
    String aka = athletes.save(AthleteDocumentMapper.toNewDocument("Aka", null)).getId();
    String ao = athletes.save(AthleteDocumentMapper.toNewDocument("Ao", null)).getId();
    String newGame =
        """
        {"format": "ELIMINATION", "medalGame": false, "extraGame": false,
         "akaAthleteId": "%s", "aoAthleteId": "%s"}
        """
            .formatted(aka, ao);
    return send(post(GAMES, newGame).header(AUTHORIZATION, basic(username, password))).statusCode();
  }

  @Test
  void admin_createsScorekeeperWhoCanSignIn() throws Exception {
    HttpResponse<String> response = createAccount("mat-one", NEW_PASSWORD, "SCOREKEEPER");

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.headers().firstValue("Location")).contains(ACCOUNTS + "/mat-one");
    assertThat(response.body()).doesNotContain(NEW_PASSWORD).doesNotContain("bcrypt");
    assertThat(statusOfNewGame("mat-one", NEW_PASSWORD)).isEqualTo(201);
    assertThat(
            send(post(ACCOUNTS, "{}").header(AUTHORIZATION, basic("mat-one", NEW_PASSWORD)))
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  void admin_createsAnotherAdmin() throws Exception {
    createAccount("second-admin", NEW_PASSWORD, "ADMIN");

    assertThat(deleteAccount(SCOREKEEPER, basic("second-admin", NEW_PASSWORD)).statusCode())
        .isEqualTo(204);
  }

  @Test
  void password_isStoredAsAnAdaptiveHash() throws Exception {
    createAccount("mat-one", NEW_PASSWORD, "SCOREKEEPER");

    assertThat(accounts.findById("mat-one").orElseThrow().getPasswordHash())
        .startsWith("{bcrypt}$2")
        .doesNotContain(NEW_PASSWORD);
  }

  /** The stored account is untouched: a second create must never work as a password change. */
  @Test
  void existingUsername_isConflictAndChangesNothing() throws Exception {
    createAccount("mat-one", NEW_PASSWORD, "SCOREKEEPER");

    assertThat(createAccount("mat-one", "another-password", "ADMIN").statusCode()).isEqualTo(409);

    assertThat(statusOfNewGame("mat-one", NEW_PASSWORD)).isEqualTo(201);
    assertThat(statusOfNewGame("mat-one", "another-password")).isEqualTo(401);
    assertThat(accounts.findById("mat-one").orElseThrow().getRole())
        .isEqualTo(AccountRole.SCOREKEEPER);
  }

  @Test
  void deletedAccount_stopsWorkingOnItsNextRequest() throws Exception {
    createAccount("mat-one", NEW_PASSWORD, "SCOREKEEPER");
    assertThat(statusOfNewGame("mat-one", NEW_PASSWORD)).isEqualTo(201);

    assertThat(deleteAccount("mat-one", adminCredentials()).statusCode()).isEqualTo(204);

    assertThat(statusOfNewGame("mat-one", NEW_PASSWORD)).isEqualTo(401);
  }

  @Test
  void deletingNobody_isNotFound() throws Exception {
    assertThat(deleteAccount("nobody", adminCredentials()).statusCode()).isEqualTo(404);
  }

  @Test
  void lastAdmin_cannotBeDeleted() throws Exception {
    assertThat(deleteAccount(ADMIN, adminCredentials()).statusCode()).isEqualTo(409);

    assertThat(accounts.existsById(ADMIN)).isTrue();
  }

  @Test
  void adminWithAnotherLeft_canBeDeleted() throws Exception {
    createAccount("second-admin", NEW_PASSWORD, "ADMIN");

    assertThat(deleteAccount(ADMIN, adminCredentials()).statusCode()).isEqualTo(204);

    assertThat(accounts.countByRole(AccountRole.ADMIN)).isEqualTo(1);
  }

  /**
   * The race the last Admin rule exists for. Both sides check before either deletes, so without the
   * write that makes them collide both succeed, and nobody can manage accounts again. Several
   * rounds, because one round can pass by luck.
   */
  @Test
  void twoAdminsDeletingEachOther_leaveOne() throws Exception {
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      for (int round = 0; round < ROUNDS; round++) {
        accounts.deleteAll();
        accounts.insert(new Account("first", "{noop}unused", AccountRole.ADMIN));
        accounts.insert(new Account("second", "{noop}unused", AccountRole.ADMIN));
        CyclicBarrier together = new CyclicBarrier(2);

        List<Future<String>> outcomes = new ArrayList<>();
        for (String username : List.of("first", "second")) {
          outcomes.add(pool.submit(() -> deleteTogether(together, username)));
        }

        List<String> results = new ArrayList<>();
        for (Future<String> outcome : outcomes) {
          results.add(outcome.get(30, TimeUnit.SECONDS));
        }

        assertThat(results).containsExactlyInAnyOrder("deleted", "refused");
        assertThat(accounts.countByRole(AccountRole.ADMIN)).isEqualTo(1);
      }
    }
  }

  private String deleteTogether(CyclicBarrier together, String username) throws Exception {
    together.await(10, TimeUnit.SECONDS);
    try {
      accountService.delete(username);
      return "deleted";
    } catch (LastAdminException e) {
      return "refused";
    }
  }

  /** Reaches the same service over HTTP, where the loser may already be gone before it signs in. */
  @Test
  void twoAdminsDeletingEachOtherOverHttp_leaveOne() throws Exception {
    createAccount("second-admin", NEW_PASSWORD, "ADMIN");
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<HttpResponse<String>> first =
          pool.submit(() -> deleteAccount("second-admin", adminCredentials()));
      Future<HttpResponse<String>> second =
          pool.submit(() -> deleteAccount(ADMIN, basic("second-admin", NEW_PASSWORD)));

      assertThat(List.of(statusOf(first), statusOf(second)))
          .contains(204)
          .allMatch(status -> List.of(204, 401, 409).contains(status));
    }

    assertThat(accounts.countByRole(AccountRole.ADMIN)).isEqualTo(1);
  }

  private static int statusOf(Future<HttpResponse<String>> response)
      throws InterruptedException, ExecutionException {
    return response.get().statusCode();
  }

  @Test
  void passwords_areNeverLogged() throws Exception {
    Logger log = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> lines = new ListAppender<>();
    lines.start();
    log.addAppender(lines);
    try {
      createAccount("mat-one", NEW_PASSWORD, "SCOREKEEPER");
      createAccount("bad:name", NEW_PASSWORD, "SCOREKEEPER");
      createAccount("mat-one", NEW_PASSWORD, "SCOREKEEPER");
      createAccount("mat-two", "é".repeat(60), "SCOREKEEPER");
    } finally {
      log.detachAppender(lines);
    }

    String logged =
        lines.list.stream()
            .map(line -> line.getFormattedMessage() + line.getThrowableProxy())
            .collect(Collectors.joining("\n"));
    assertThat(lines.list).isNotEmpty();
    assertThat(logged).doesNotContain(NEW_PASSWORD).doesNotContain("$2a$");
  }
}

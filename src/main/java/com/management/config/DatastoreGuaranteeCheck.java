package com.management.config;

import com.mongodb.connection.ServerType;
import com.mongodb.event.ServerDescriptionChangedEvent;
import com.mongodb.event.ServerListener;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Refuses to run on a reachable datastore that cannot give the guarantees the application needs.
 *
 * <p>An unreachable datastore is an outage, not a weak one: the application starts, health reports
 * DOWN, and the check runs when the datastore first answers. See {@code [same-guarantees]} in
 * {@code context/workflow/design/acid.md}.
 *
 * <p><strong>Why a listener and not a {@code hello} command.</strong> With {@code replicaSet} in
 * the connection, the driver drops a standalone from its view, so every command through the client
 * times out exactly as it does on an unreachable server. The listener is told what the server is
 * before that happens, in every connection shape.
 *
 * <p><strong>Why this runs before the singletons finish.</strong> {@link
 * #afterSingletonsInstantiated()} is called before the web server starts listening, so a refused
 * datastore never serves a request. An {@code ApplicationRunner} would run too late.
 *
 * <p><strong>A datastore that passes is announced.</strong> {@link DatastoreAccepted} lets what
 * needs the datastore run after this check, at whichever moment it first answers.
 *
 * <p><strong>A member that is not initiated refuses only at startup.</strong> A replica set member
 * can also report this state for a moment as it restarts, so it cannot stop a running application.
 * The driver will not write to it either way.
 */
@Component
class DatastoreGuaranteeCheck implements ServerListener, SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(DatastoreGuaranteeCheck.class);

  private final CompletableFuture<ServerType> firstAnswer = new CompletableFuture<>();
  private final DatastoreProperties properties;
  private final ConfigurableApplicationContext context;
  private volatile ServerType latest = ServerType.UNKNOWN;

  DatastoreGuaranteeCheck(DatastoreProperties properties, ConfigurableApplicationContext context) {
    this.properties = properties;
    this.context = context;
  }

  @Override
  public void serverDescriptionChanged(ServerDescriptionChangedEvent event) {
    latest = event.getNewDescription().getType();
    if (latest != ServerType.REPLICA_SET_GHOST && latest != ServerType.UNKNOWN) {
      firstAnswer.complete(latest);
    }
  }

  @Override
  public void afterSingletonsInstantiated() {
    try {
      if (isStandalone(
          firstAnswer.get(properties.startupWait().toMillis(), TimeUnit.MILLISECONDS))) {
        throw new IllegalStateException(standaloneMessage());
      }
      context.publishEvent(new DatastoreAccepted());
    } catch (TimeoutException e) {
      if (latest == ServerType.REPLICA_SET_GHOST) {
        throw new IllegalStateException(
            "The datastore is a replica set member that was never initiated. Run rs.initiate.", e);
      }
      log.warn("The datastore did not answer at startup; checking it when it does");
      firstAnswer
          .thenAcceptAsync(this::acceptOrStop)
          .exceptionally(failure -> {
            log.error("Stopping on a refused datastore failed", failure);
            return null;
          });
    } catch (ExecutionException e) {
      throw new IllegalStateException("The datastore check failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while checking the datastore", e);
    }
  }

  /** Runs off the driver's monitor thread, because closing the context closes the client. */
  private void acceptOrStop(ServerType type) {
    try {
      if (isStandalone(type)) {
        throw new IllegalStateException(standaloneMessage());
      }
      context.publishEvent(new DatastoreAccepted());
    } catch (IllegalStateException e) {
      log.error("{} Stopping.", e.getMessage());
      context.close();
    }
  }

  private static boolean isStandalone(ServerType type) {
    return type == ServerType.STANDALONE;
  }

  private static String standaloneMessage() {
    return "The datastore is a standalone server: it has no transactions or retryable writes.";
  }
}

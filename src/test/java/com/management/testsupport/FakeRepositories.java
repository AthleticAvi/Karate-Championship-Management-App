package com.management.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.repositories.KumiteGameRepository;
import com.management.repositories.PlayerRepository;

/**
 * Repository doubles backed by {@link InMemoryMongo}, so every read is a real conversion round
 * trip.
 *
 * <p>Built with Mockito rather than hand-written classes. {@code MongoRepository} declares around
 * forty methods and the services use three; implementing the rest to throw would be a hand-rolled
 * version of what the mocking framework already does. Any method not wired here returns Mockito's
 * default, which is the correct signal that a test relies on behaviour this double does not model.
 */
public final class FakeRepositories {

  private FakeRepositories() {}

  /** A game repository whose {@code findById} returns a genuinely reloaded object. */
  public static KumiteGameRepository kumiteGames(InMemoryMongo storage) {
    KumiteGameRepository repository = mock(KumiteGameRepository.class);

    when(repository.save(any(KumiteGame.class)))
        .thenAnswer(call -> storage.save(call.getArgument(0)));
    when(repository.findById(anyString()))
        .thenAnswer(call -> storage.findById(KumiteGame.class, call.getArgument(0)));
    doAnswer(
            call -> {
              storage.delete(call.getArgument(0));
              return null;
            })
        .when(repository)
        .delete(any(KumiteGame.class));

    return repository;
  }

  /** A player repository whose {@code findById} returns a genuinely reloaded object. */
  public static PlayerRepository players(InMemoryMongo storage) {
    PlayerRepository repository = mock(PlayerRepository.class);

    when(repository.save(any(Player.class))).thenAnswer(call -> storage.save(call.getArgument(0)));
    when(repository.findById(anyString()))
        .thenAnswer(call -> storage.findById(Player.class, call.getArgument(0)));
    doAnswer(
            call -> {
              storage.delete(call.getArgument(0));
              return null;
            })
        .when(repository)
        .delete(any(Player.class));

    return repository;
  }
}

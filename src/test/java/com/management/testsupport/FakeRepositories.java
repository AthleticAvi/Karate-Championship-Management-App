package com.management.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.management.kumitegame.data.KumiteGameDocument;
import com.management.kumitegame.data.KumiteGameRepository;

/**
 * Repository doubles backed by {@link InMemoryMongo}, so every read is a real conversion round
 * trip.
 *
 * <p>Built with Mockito rather than hand-written classes. {@code MongoRepository} declares around
 * forty methods and the services use a few; implementing the rest to throw would be a hand-rolled
 * version of what the mocking framework already does. Any method not wired here returns Mockito's
 * default, which is the correct signal that a test relies on behaviour this double does not model.
 * Neither is the version check: a test about a lost race makes {@code save} throw.
 */
public final class FakeRepositories {

  private FakeRepositories() {}

  /** A game repository whose {@code findById} returns a genuinely reloaded document. */
  public static KumiteGameRepository kumiteGames(InMemoryMongo storage) {
    KumiteGameRepository repository = mock(KumiteGameRepository.class);

    when(repository.save(any(KumiteGameDocument.class)))
        .thenAnswer(call -> storage.save(call.getArgument(0)));
    when(repository.insert(any(KumiteGameDocument.class)))
        .thenAnswer(call -> storage.save(call.getArgument(0)));
    when(repository.findById(anyString()))
        .thenAnswer(call -> storage.findById(KumiteGameDocument.class, call.getArgument(0)));

    return repository;
  }
}

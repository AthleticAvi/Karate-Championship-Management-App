package com.management.testsupport;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;

/**
 * A persistence double that behaves like MongoDB, without a MongoDB.
 *
 * <p><strong>Why this exists.</strong> The obvious way to fake a repository is to stub {@code save}
 * to return its own argument. That is the most common false-green in a test suite: anything the
 * real database would not keep survives in the double, so the test passes against code that crashes
 * in production. This project had exactly that, and it is why five green tests sat on top of a
 * guaranteed {@code NullPointerException}.
 *
 * <p><strong>How it avoids that.</strong> Entities are converted to a BSON {@link Document} on save
 * and converted back on read, using Spring Data's own {@link MappingMongoConverter} — the same
 * component that does it in production. The double therefore cannot drift from what MongoDB
 * actually stores: it does not reimplement the rules about {@code @Transient} fields, it runs them.
 * A field excluded from persistence comes back absent because the production mapping layer excluded
 * it, not because this class remembered to.
 *
 * <p>No Docker, no container, no network. A round trip costs microseconds, so this belongs in the
 * fast suite. Tests that need to assert real database behaviour — indexes, queries, concurrency —
 * want real infrastructure instead.
 */
public final class InMemoryMongo {

  private final MappingMongoConverter converter;
  private final MongoMappingContext context;
  private final Map<Class<?>, Map<String, Document>> collections = new ConcurrentHashMap<>();

  public InMemoryMongo() {
    MongoCustomConversions conversions = new MongoCustomConversions(List.of());
    this.context = new MongoMappingContext();
    this.context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
    this.context.afterPropertiesSet();

    this.converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, this.context);
    this.converter.setCustomConversions(conversions);
    this.converter.afterPropertiesSet();
  }

  /**
   * Stores the entity and returns what a subsequent read would produce.
   *
   * <p>The returned object is never the one passed in — that non-identity is the whole point of
   * this class. Assigns an identifier when the entity does not already have one, as an insert does.
   *
   * <p><strong>The identifier is also written back onto the entity passed in</strong>, which is
   * what {@code MongoRepository.save} does. Leaving it off looked harmless — code that reads the id
   * from the argument rather than the return value would simply fail in tests — but it made a
   * second save of the same instance insert a second document instead of updating the first, and
   * left {@link #delete} unable to find anything to remove. Both of those are false greens, in a
   * class whose entire purpose is to remove them.
   */
  public <T> T save(T entity) {
    Document document = new Document();
    converter.write(entity, document);

    String id = Optional.ofNullable(document.get("_id")).map(Object::toString).orElse(null);
    if (id == null) {
      id = UUID.randomUUID().toString();
      document.put("_id", id);
      assignIdentifier(entity, id);
    }

    collectionFor(entity.getClass()).put(id, document);

    @SuppressWarnings("unchecked")
    Class<T> type = (Class<T>) entity.getClass();
    return read(type, document);
  }

  /** Sets the generated identifier on the saved instance, as the real mapping layer does. */
  private void assignIdentifier(Object entity, String id) {
    MongoPersistentEntity<?> persistentEntity =
        context.getRequiredPersistentEntity(entity.getClass());
    MongoPersistentProperty idProperty = persistentEntity.getIdProperty();
    if (idProperty != null) {
      persistentEntity.getPropertyAccessor(entity).setProperty(idProperty, id);
    }
  }

  /** Returns a freshly converted instance, or empty. Never returns a stored reference. */
  public <T> Optional<T> findById(Class<T> type, String id) {
    Document stored = collectionFor(type).get(id);
    return Optional.ofNullable(stored).map(document -> read(type, document));
  }

  /**
   * Removes the entity by its identifier.
   *
   * <p>Rejects an entity with no identifier rather than quietly doing nothing. Silently succeeding
   * is how {@code save(p); delete(p);} used to leave the document in storage while reporting that
   * it had gone — a test asserting the deletion would pass against code that never deleted
   * anything.
   */
  public void delete(Object entity) {
    MongoPersistentEntity<?> persistentEntity =
        context.getRequiredPersistentEntity(entity.getClass());
    Object id = persistentEntity.getIdentifierAccessor(entity).getIdentifier();
    if (id == null) {
      throw new IllegalArgumentException(
          "Cannot delete a "
              + entity.getClass().getSimpleName()
              + " with no identifier: it was never saved, so there is nothing to remove.");
    }
    collectionFor(entity.getClass()).remove(id.toString());
  }

  /** Number of stored documents for a type. */
  public int count(Class<?> type) {
    return collectionFor(type).size();
  }

  /**
   * Converts an entity to the document that would be stored, without storing it.
   *
   * <p>For tests that assert the persisted <em>representation</em> rather than round-trip behaviour
   * — the shape on disk is a contract too, and a framework upgrade can change it silently.
   */
  public Document writeForInspection(Object entity) {
    Document document = new Document();
    converter.write(entity, document);
    return document;
  }

  /** Discards everything, so a test never inherits another test's data. */
  public void clear() {
    collections.clear();
  }

  /**
   * Converts a stored document back into an entity.
   *
   * <p>Deep-copies the document first, so a caller mutating the returned entity cannot reach back
   * into stored state — the same isolation a real driver gives.
   *
   * <p>The copy used to be {@code new Document(new HashMap<>(stored))}, which copies the top level
   * and leaves every nested {@code Document} and {@code List} shared with storage. No leak was ever
   * observed: this converter rebuilds the value of every property it reads, including loosely-typed
   * ones, so the shallow copy was in practice indistinguishable from a deep one.
   *
   * <p>It is deep anyway, because the paragraph above states a guarantee and the code should be the
   * reason it holds rather than an implementation detail of a dependency. <strong>No test here can
   * fail on the difference</strong> — that was checked by reverting to the shallow copy and running
   * this class, which stayed green, and a test that cannot fail is not worth keeping.
   */
  private <T> T read(Class<T> type, Document stored) {
    return converter.read(type, deepCopy(stored));
  }

  /** Copies a document and everything nested inside it. */
  private static Document deepCopy(Document source) {
    Document copy = new Document();
    source.forEach((key, value) -> copy.put(key, copyValue(value)));
    return copy;
  }

  private static @Nullable Object copyValue(@Nullable Object value) {
    if (value instanceof Document document) {
      return deepCopy(document);
    }
    if (value instanceof List<?> list) {
      return list.stream().map(InMemoryMongo::copyValue).toList();
    }
    // Everything else Mongo stores is immutable: strings, numbers, booleans, dates, ObjectIds.
    return value;
  }

  private Map<String, Document> collectionFor(Class<?> type) {
    return collections.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>());
  }
}

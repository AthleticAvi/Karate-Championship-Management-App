package com.management.testsupport;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;

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
    MongoCustomConversions conversions = new MongoCustomConversions(java.util.List.of());
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
   * <p>The returned object is never the one passed in. Assigns an identifier when the entity does
   * not already have one, as an insert does.
   */
  public <T> T save(T entity) {
    Document document = new Document();
    converter.write(entity, document);

    String id = Optional.ofNullable(document.get("_id")).map(Object::toString).orElse(null);
    if (id == null) {
      id = UUID.randomUUID().toString();
      document.put("_id", id);
    }

    collectionFor(entity.getClass()).put(id, document);

    @SuppressWarnings("unchecked")
    Class<T> type = (Class<T>) entity.getClass();
    return read(type, document);
  }

  /** Returns a freshly converted instance, or empty. Never returns a stored reference. */
  public <T> Optional<T> findById(Class<T> type, String id) {
    Document stored = collectionFor(type).get(id);
    return Optional.ofNullable(stored).map(document -> read(type, document));
  }

  /** Removes the entity by its identifier. */
  public void delete(Object entity) {
    MongoPersistentEntity<?> persistentEntity =
        context.getRequiredPersistentEntity(entity.getClass());
    Object id = persistentEntity.getIdentifierAccessor(entity).getIdentifier();
    if (id != null) {
      collectionFor(entity.getClass()).remove(id.toString());
    }
  }

  /** Number of stored documents for a type. */
  public int count(Class<?> type) {
    return collectionFor(type).size();
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
   */
  private <T> T read(Class<T> type, Document stored) {
    return converter.read(type, new Document(new HashMap<>(stored)));
  }

  private Map<String, Document> collectionFor(Class<?> type) {
    return collections.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>());
  }
}

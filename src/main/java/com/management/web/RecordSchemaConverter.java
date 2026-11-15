package com.management.web;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * States on a record's schema what the API enforces on it and the generator leaves out.
 *
 * <ul>
 *   <li>A component of a primitive type is required: Jackson reads a missing one as null, and
 *       refuses null for a primitive.
 *   <li>{@code @NotBlank} text needs a character that is not a space, which the generator does not
 *       say: it writes the {@code minLength} of a {@code @Size}, often 0. The pattern says it, and
 *       so says the text is not empty.
 *   <li>{@code @Pattern} matches the whole value, but a pattern in the description matches anywhere
 *       in it, so it is anchored.
 *   <li>The generator reads the constraints on a list's items, but not those on a map's keys and
 *       values. They are resolved here the way the generator resolves an item. No map holds
 *       records: such values would be written out whole, without their JSON view.
 * </ul>
 *
 * <p>A converter rather than a property customizer, because only the record holds a component's
 * type arguments and their annotations: a property customizer sees neither.
 */
final class RecordSchemaConverter implements ModelConverter {

  /**
   * Any character that {@link String#isBlank} does not count as space, which is how Bean Validation
   * checks {@code @NotBlank}. Written out, because {@code \S} means a different set in Java and in
   * the description's regular expressions.
   */
  static final String NOT_BLANK =
      "[^\\t\\n\\u000B\\f\\r\\u001C-\\u001F \\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029"
          + "\\u205F\\u3000]";

  @Override
  @SuppressWarnings("rawtypes")
  public @Nullable Schema resolve(
      AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
    if (!chain.hasNext()) {
      return null;
    }
    Schema<?> resolved = chain.next().resolve(type, context, chain);
    Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
    if (resolved == null || !raw.isRecord()) {
      return resolved;
    }
    Schema<?> record =
        resolved.get$ref() == null
            ? resolved
            : context
                .getDefinedModels()
                .get(resolved.get$ref().substring(Components.COMPONENTS_SCHEMAS_REF.length()));
    if (record == null || record.getProperties() == null) {
      return resolved;
    }
    for (RecordComponent component : raw.getRecordComponents()) {
      // A JSON view can leave a component out of the schema.
      Schema<?> property = record.getProperties().get(component.getName());
      if (property != null) {
        state(component, property, record, context);
      }
    }
    return resolved;
  }

  private static void state(
      RecordComponent component,
      Schema<?> property,
      Schema<?> record,
      ModelConverterContext context) {
    if (component.getType().isPrimitive()) {
      record.addRequiredItem(component.getName());
    }
    // Read from the component's type, which keeps them when a record declares its own accessor.
    if (component.getAnnotatedType().isAnnotationPresent(NotBlank.class)) {
      property.setPattern(NOT_BLANK);
    }
    Pattern pattern = component.getAnnotatedType().getAnnotation(Pattern.class);
    if (pattern != null) {
      property.setPattern("^(?:" + pattern.regexp() + ")$");
    }
    if (Map.class.isAssignableFrom(component.getType())
        && component.getAnnotatedType() instanceof AnnotatedParameterizedType map) {
      java.lang.reflect.AnnotatedType[] arguments = map.getAnnotatedActualTypeArguments();
      property.setPropertyNames(context.resolve(asValue(arguments[0])));
      property.setAdditionalProperties(context.resolve(asValue(arguments[1])));
    }
  }

  /** A type argument as the generator resolves a list's item: with its constraints. */
  private static AnnotatedType asValue(java.lang.reflect.AnnotatedType argument) {
    return new AnnotatedType(argument.getType())
        .ctxAnnotations(argument.getAnnotations())
        .schemaProperty(true);
  }
}

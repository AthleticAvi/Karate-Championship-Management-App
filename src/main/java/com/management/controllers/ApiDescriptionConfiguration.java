package com.management.controllers;

import com.management.filters.RequestCorrelationFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Completes the generated API description with what the generator cannot see.
 *
 * <p>It reads a status only from an annotation, and the exception handler sets statuses at runtime,
 * so on its own it lists no failure at all. The rules in {@link #addFailures} state which failures
 * each kind of operation can meet, from the table in {@code
 * src/main/java/com/management/exceptions/README.md}. They apply to every operation, so a new
 * endpoint is described with no extra work here.
 *
 * <p>The problem schema is written out because the framework's {@code ProblemDetail} class does not
 * match its own wire form: extension fields sit at the top level, and an unset type is not sent.
 * {@code ApiDescriptionIT} compares it with real answers.
 */
@Configuration(proxyBeanMethods = false)
class ApiDescriptionConfiguration {

  private static final String PROBLEM_SCHEMA = "ProblemDetail";
  private static final String NULL_TYPE = "null";

  @Bean
  OpenApiCustomizer apiDescriptionCustomizer() {
    return ApiDescriptionConfiguration::complete;
  }

  private static void complete(OpenAPI description) {
    var schemas = description.getComponents().getSchemas();
    schemas.values().forEach(ApiDescriptionConfiguration::allowNullInEnums);
    description
        .getPaths()
        .values()
        .forEach(path ->
            path.readOperationsMap()
                .forEach((method, operation) -> {
                  operation
                      .getResponses()
                      .values()
                      .forEach(response -> requireEveryField(response, schemas));
                  addFailures(method, operation);
                  addHeaders(operation);
                }));
    schemas.put(PROBLEM_SCHEMA, problemSchema());
  }

  /**
   * A null-marked enum still lists only its constants, which rejects the null it allows.
   *
   * <p>The generator marks the type as nullable and leaves the list of values alone.
   */
  private static void allowNullInEnums(Schema<?> schema) {
    if (schema.getProperties() != null) {
      schema.getProperties().values().forEach(ApiDescriptionConfiguration::allowNullInEnum);
    }
  }

  private static <T> void allowNullInEnum(Schema<T> property) {
    boolean nullable = property.getTypes() != null && property.getTypes().contains(NULL_TYPE);
    if (nullable && property.getEnum() != null && !property.getEnum().contains(null)) {
      property.addEnumItemObject(null);
    }
  }

  /** A response always carries every field, with null for a value that is absent. */
  private static void requireEveryField(ApiResponse response, Map<String, ?> schemas) {
    if (response.getContent() != null) {
      response
          .getContent()
          .values()
          .forEach(media -> requireEveryField(media.getSchema(), schemas));
    }
  }

  private static void requireEveryField(Schema<?> reference, Map<String, ?> schemas) {
    if (reference.get$ref() == null) {
      return;
    }
    String name = reference.get$ref().substring(Components.COMPONENTS_SCHEMAS_REF.length());
    Schema<?> schema = (Schema<?>) schemas.get(name);
    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
    schema.getProperties().values().forEach(field -> requireEveryField(field, schemas));
  }

  private static void addFailures(PathItem.HttpMethod method, Operation operation) {
    boolean changesExisting =
        method == PathItem.HttpMethod.PUT || method == PathItem.HttpMethod.DELETE;
    boolean takesInput = method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PUT;
    if (takesInput) {
      addFailure(operation, HttpStatus.BAD_REQUEST);
    }
    if (hasPathParameter(operation)) {
      addFailure(operation, HttpStatus.NOT_FOUND);
    }
    if (changesExisting) {
      addFailure(operation, HttpStatus.CONFLICT);
    }
    if (operation.getRequestBody() != null) {
      addFailure(operation, HttpStatus.CONTENT_TOO_LARGE);
      addFailure(operation, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
    addFailure(operation, HttpStatus.NOT_ACCEPTABLE);
    addFailure(operation, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private static boolean hasPathParameter(Operation operation) {
    List<Parameter> parameters = operation.getParameters();
    return parameters != null
        && parameters.stream().anyMatch(parameter -> "path".equals(parameter.getIn()));
  }

  private static void addFailure(Operation operation, HttpStatus status) {
    Schema<?> problem = new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + PROBLEM_SCHEMA);
    operation
        .getResponses()
        .addApiResponse(
            String.valueOf(status.value()),
            new ApiResponse()
                .description(status.getReasonPhrase())
                .content(
                    new Content()
                        .addMediaType(
                            MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            new io.swagger.v3.oas.models.media.MediaType().schema(problem))));
  }

  private static void addHeaders(Operation operation) {
    operation
        .getResponses()
        .forEach((status, response) -> {
          response.addHeaderObject(
              RequestCorrelationFilter.HEADER,
              header("Ties this answer to the server's log lines"));
          if (String.valueOf(HttpStatus.CREATED.value()).equals(status)) {
            response.addHeaderObject(
                HttpHeaders.LOCATION, header("The relative path of what was created"));
          }
        });
  }

  private static Header header(String description) {
    return new Header().description(description).schema(new StringSchema());
  }

  private static Schema<?> problemSchema() {
    return new ObjectSchema()
        .description("An RFC 9457 problem detail")
        .addProperty("title", new StringSchema())
        .addProperty("status", new IntegerSchema())
        .addProperty("detail", new StringSchema())
        .addProperty("instance", new StringSchema())
        .addProperty(
            "errors",
            new MapSchema()
                .additionalProperties(new StringSchema())
                .description("Each failing field and why, on a failed validation only"))
        .required(List.of("title", "status", "detail", "instance"));
  }
}

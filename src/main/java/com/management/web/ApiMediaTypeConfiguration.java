package com.management.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Keeps the API on JSON, whatever a library leaves on the classpath, and reads each JSON value only
 * as its own type.
 *
 * <p>The framework adds a YAML reader and writer as soon as the YAML data format is present, and
 * the API description library needs it there. Left in, every endpoint would accept and answer YAML
 * in every profile, and the JSON size limit would not apply to those bodies. See {@code [server]}
 * in {@code src/main/java/com/management/config/README.md}.
 *
 * <p>A web configuration, so a controller slice reads a body as the application does.
 */
@Configuration(proxyBeanMethods = false)
class ApiMediaTypeConfiguration implements WebMvcConfigurer {

  @Override
  public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
    builder.configureMessageConvertersList(converters ->
        converters.removeIf(
            converter -> converter.getSupportedMediaTypes().contains(MediaType.APPLICATION_YAML)));
  }

  /**
   * A number or a boolean sent for text is refused, not turned into text. The rest of the types are
   * held by properties in {@code application.properties}, which do not reach text.
   */
  @Bean
  JsonMapperBuilderCustomizer textTakesOnlyText() {
    return builder ->
        builder.withCoercionConfig(
            LogicalType.Textual,
            text ->
                text.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
  }
}

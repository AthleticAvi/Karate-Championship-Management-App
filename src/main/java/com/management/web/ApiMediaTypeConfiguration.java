package com.management.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps the API on JSON, whatever a library leaves on the classpath.
 *
 * <p>The framework adds a YAML reader and writer as soon as the YAML data format is present, and
 * the API description library needs it there. Left in, every endpoint would accept and answer YAML
 * in every profile, and the JSON size limit would not apply to those bodies. See {@code [server]}
 * in {@code src/main/java/com/management/config/README.md}.
 */
@Configuration(proxyBeanMethods = false)
class ApiMediaTypeConfiguration implements WebMvcConfigurer {

  @Override
  public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
    builder.configureMessageConvertersList(converters ->
        converters.removeIf(
            converter -> converter.getSupportedMediaTypes().contains(MediaType.APPLICATION_YAML)));
  }
}

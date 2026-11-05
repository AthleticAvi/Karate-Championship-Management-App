package com.management.controllers;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The one place the API version is named: every controller in this package answers under it.
 *
 * <p>Scoped to this package, so the actuator, the error path and any library controller stay where
 * they are. A constant and not a setting, because a client depends on the path: it is a contract,
 * not something a deployment tunes.
 */
@Configuration(proxyBeanMethods = false)
class ApiVersionConfiguration implements WebMvcConfigurer {

  static final String PREFIX = "/api/v1";

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(
        PREFIX, HandlerTypePredicate.forBasePackageClass(ApiVersionConfiguration.class));
  }
}

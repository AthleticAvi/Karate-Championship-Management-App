package com.management.web;

import com.management.config.CorsProperties;
import com.management.filters.RequestCorrelationFilter;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Which browsers may call the API, and on what terms. Only the API: the actuator stays as it is.
 *
 * <p><strong>A filter and not the framework's MVC mapping.</strong> A preflight carries no
 * credentials, so CORS has to run before any authentication, and this filter sits ahead of Spring
 * Security's chain, which has its own CORS filter turned off. It also answers inside the
 * correlation filter, so a refusal carries an ID and a request line, and it covers answers given
 * before a handler is chosen, such as a 401.
 *
 * <p><strong>Scoped twice on purpose.</strong> The servlet mapping keeps the filter off every other
 * path, because it ends any preflight it sees, even one with no configuration. The source states
 * the same scope for whatever else reads it.
 *
 * <p>The bean is not named {@code corsFilter}: Spring Security looks that name up and expects a
 * plain {@link CorsFilter}. Credentials travel in the {@code Authorization} header, so nothing here
 * allows cookies.
 */
@Configuration(proxyBeanMethods = false)
class ApiCorsConfiguration {

  /** After the correlation filter and the framework's observation filter, ahead of security. */
  private static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    CorsConfiguration api = new CorsConfiguration();
    api.setAllowedOrigins(properties.allowedOrigins());
    api.setAllowedMethods(
        List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.DELETE.name()));
    api.setAllowedHeaders(
        List.of(
            HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, RequestCorrelationFilter.HEADER));
    api.setExposedHeaders(List.of(RequestCorrelationFilter.HEADER, HttpHeaders.LOCATION));
    api.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration(ApiVersionConfiguration.PREFIX + "/**", api);
    return source;
  }

  @Bean
  FilterRegistrationBean<CorsFilter> apiCorsFilter(UrlBasedCorsConfigurationSource source) {
    FilterRegistrationBean<CorsFilter> registration =
        new FilterRegistrationBean<>(new CorsFilter(source));
    registration.addUrlPatterns(ApiVersionConfiguration.PREFIX + "/*");
    registration.setOrder(FILTER_ORDER);
    return registration;
  }
}

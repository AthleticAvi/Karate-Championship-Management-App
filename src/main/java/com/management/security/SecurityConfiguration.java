package com.management.security;

import com.management.controllers.ApiAccess;
import com.management.enums.AccountRole;
import com.management.services.AccountService;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * HTTP Basic against the accounts in the datastore, stateless, with the role table applied.
 *
 * <p><strong>CSRF is off because no browser sends these credentials by itself.</strong> Nothing
 * here sets a cookie, and a 401 carries no challenge, so a browser never shows its login prompt and
 * never starts resending a password. See {@link SecurityFailureResponder}.
 *
 * <p><strong>CORS is off in the chain because it already happened.</strong> Spring Security adds
 * its own filter when it finds the one source bean, and that filter would refuse a preflight on any
 * path outside the source, such as the actuator. The API's filter runs ahead of this chain, so a
 * preflight never gets here.
 *
 * <p><strong>Everything unlisted is denied, whoever asks.</strong> The last rule is {@code
 * denyAll}, not {@code authenticated}, so a new endpoint is not open to every Scorekeeper until
 * someone remembers it.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {

  private static final String ADMIN = AccountRole.ADMIN.name();
  private static final String SCOREKEEPER = AccountRole.SCOREKEEPER.name();

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
    SecurityFailureResponder failures = new SecurityFailureResponder(exceptionResolver);
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(basic -> basic.authenticationEntryPoint(failures))
        .exceptionHandling(handling -> handling.accessDeniedHandler(failures))
        .authorizeHttpRequests(SecurityConfiguration::authorize)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  UserDetailsService accountUserDetailsService(AccountService accounts) {
    return new AccountUserDetailsService(accounts);
  }

  private static void authorize(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          rules) {
    // A failure the container answers comes back through here, and must keep its own status.
    rules.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
    ApiAccess.RULES.forEach(rule -> {
      var endpoint = rules.requestMatchers(rule.method(), rule.path());
      if (rule.level() == ApiAccess.Level.PUBLIC) {
        endpoint.permitAll();
      } else if (rule.level() == ApiAccess.Level.SCOREKEEPER) {
        endpoint.hasAnyRole(SCOREKEEPER, ADMIN);
      } else {
        endpoint.hasRole(ADMIN);
      }
    });
    rules
        .requestMatchers(HttpMethod.GET, "/actuator/health/liveness", "/actuator/health/readiness")
        .permitAll();
    // Only a profile that serves the description has anything here: elsewhere this is a 404.
    rules.requestMatchers(HttpMethod.GET, "/v3/api-docs.yaml", "/v3/api-docs/**").permitAll();
    rules.requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole(ADMIN);
    rules.anyRequest().denyAll();
  }
}

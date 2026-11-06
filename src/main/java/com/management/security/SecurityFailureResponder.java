package com.management.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Hands an authentication or authorization failure to {@code GlobalExceptionHandler}, so it answers
 * as every other failure does.
 *
 * <p><strong>A 401 here carries no {@code WWW-Authenticate: Basic}, on purpose.</strong> HTTP says
 * a 401 sends a challenge. This one would make a browser show its login prompt and then resend the
 * credentials by itself on every request, which is what would make turning CSRF off unsafe.
 *
 * <p>The status is set first, so a failure in the handler still answers the refusal.
 */
final class SecurityFailureResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final HandlerExceptionResolver exceptionResolver;

  SecurityFailureResponder(HandlerExceptionResolver exceptionResolver) {
    this.exceptionResolver = exceptionResolver;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException failure) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    exceptionResolver.resolveException(request, response, null, failure);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure) {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    exceptionResolver.resolveException(request, response, null, failure);
  }
}

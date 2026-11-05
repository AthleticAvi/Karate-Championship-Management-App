package com.management.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Ties a request to its log lines: gives it a correlation ID, returns the ID, and logs one line
 * when the request ends.
 *
 * <p>First in the chain, so everything after it logs with the ID. The header is set before the
 * chain runs, so a failed request carries it too.
 *
 * <p>The line holds only what this application vouches for. The route is the mapped pattern and not
 * the path, and an unknown method is not repeated, because both would otherwise reflect what a
 * client sent. See {@code [never-log]} in {@code context/workflow/design/logging.md}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Correlation-ID";
  static final String MDC_KEY = "correlationId";
  static final String UNMATCHED_ROUTE = "UNMATCHED";
  static final String OTHER_METHOD = "OTHER";

  private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

  /** Safe to log and to echo in a header: nothing here can end a line or break out of a field. */
  private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  private static final Set<String> STANDARD_METHODS =
      Arrays.stream(HttpMethod.values()).map(HttpMethod::name).collect(Collectors.toSet());

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = correlationIdOf(request);
    response.setHeader(HEADER, correlationId);
    MDC.put(MDC_KEY, correlationId);
    long started = System.nanoTime();
    // What the container answers when the chain throws, because the response does not know yet.
    int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    try {
      chain.doFilter(request, response);
      status = response.getStatus();
    } finally {
      // A committed response has been answered, whatever failed after it.
      logRequest(request, response.isCommitted() ? response.getStatus() : status, started);
      MDC.remove(MDC_KEY);
    }
  }

  private static String correlationIdOf(HttpServletRequest request) {
    String incoming = request.getHeader(HEADER);
    return incoming != null && VALID_ID.matcher(incoming).matches()
        ? incoming
        : UUID.randomUUID().toString();
  }

  private static void logRequest(HttpServletRequest request, int status, long started) {
    log.atInfo()
        .addKeyValue("method", methodOf(request))
        .addKeyValue("route", routeOf(request))
        .addKeyValue("status", status)
        .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
        .log("Request completed");
  }

  private static String methodOf(HttpServletRequest request) {
    String method = request.getMethod();
    return STANDARD_METHODS.contains(method) ? method : OTHER_METHOD;
  }

  private static String routeOf(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern == null ? UNMATCHED_ROUTE : pattern.toString();
  }
}

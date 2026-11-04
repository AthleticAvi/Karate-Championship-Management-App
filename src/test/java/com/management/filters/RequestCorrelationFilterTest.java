package com.management.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

/** The filter on its own: which ID a request gets, and what its one line says. */
class RequestCorrelationFilterTest {

  private static final String ROUTE = "/api/v1/players/{playerId}";

  private final RequestCorrelationFilter filter = new RequestCorrelationFilter();
  private final Logger log = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
  private final ListAppender<ILoggingEvent> lines =
      new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent line) {
          line.prepareForDeferredProcessing();
          super.append(line);
        }
      };

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest("GET", "/api/v1/players/player-1");
    response = new MockHttpServletResponse();
    lines.start();
    log.addAppender(lines);
  }

  @AfterEach
  void tearDown() {
    log.detachAppender(lines);
  }

  @Test
  void request_withNoId_getsGeneratedOne() throws Exception {
    filter.doFilter(request, response, new MockFilterChain());

    assertThat(UUID.fromString(returnedId())).isNotNull();
  }

  @Test
  void request_withValidId_keepsIt() throws Exception {
    request.addHeader(RequestCorrelationFilter.HEADER, "upstream_id-42");

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(returnedId()).isEqualTo("upstream_id-42");
  }

  @Test
  void id_ofTheLongestAllowedLength_isKept() throws Exception {
    String longest = "a".repeat(64);
    request.addHeader(RequestCorrelationFilter.HEADER, longest);

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(returnedId()).isEqualTo(longest);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "has space", "line\nbreak", "quote\"", "{json}", "ünicode"})
  void request_withInvalidId_getsGeneratedOne(String invalid) throws Exception {
    request.addHeader(RequestCorrelationFilter.HEADER, invalid);

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(returnedId()).isNotEqualTo(invalid);
    assertThat(UUID.fromString(returnedId())).isNotNull();
  }

  @Test
  void id_pastTheLongestAllowedLength_isReplaced() throws Exception {
    String tooLong = "a".repeat(65);
    request.addHeader(RequestCorrelationFilter.HEADER, tooLong);

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(returnedId()).isNotEqualTo(tooLong);
  }

  @Test
  void id_isInTheDiagnosticContextDuringTheRequestAndGoneAfterIt() throws Exception {
    AtomicReference<String> duringRequest = new AtomicReference<>();
    FilterChain chain = (req, res) -> duringRequest.set(MDC.get(RequestCorrelationFilter.MDC_KEY));

    filter.doFilter(request, response, chain);

    assertThat(duringRequest.get()).isEqualTo(returnedId());
    assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
  }

  @Test
  void request_logsOneLineCarryingItsIdAndWhatHappened() throws Exception {
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, ROUTE);
    FilterChain chain = (req, res) -> response.setStatus(404);

    filter.doFilter(request, response, chain);

    assertThat(lines.list).hasSize(1);
    ILoggingEvent line = lines.list.get(0);
    assertThat(line.getMDCPropertyMap())
        .containsEntry(RequestCorrelationFilter.MDC_KEY, returnedId());
    assertThat(line.getMessage()).isEqualTo("Request completed");
    assertThat(fields(line))
        .containsEntry("method", "GET")
        .containsEntry("route", ROUTE)
        .containsEntry("status", 404)
        .containsKey("durationMs");
  }

  @Test
  void line_neverRepeatsWhatTheClientPutInThePathOrQuery() throws Exception {
    request.setRequestURI("/api/v1/players/client-value");
    request.setQueryString("token=client-secret");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, ROUTE);

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(lines.list.get(0).getKeyValuePairs().toString())
        .doesNotContain("client-value")
        .doesNotContain("client-secret");
  }

  @Test
  void request_thatNoRouteMatched_saysSoRatherThanRepeatingThePath() throws Exception {
    filter.doFilter(request, response, new MockFilterChain());

    assertThat(fields(lines.list.get(0)))
        .containsEntry("route", RequestCorrelationFilter.UNMATCHED_ROUTE);
  }

  @Test
  void unknownMethod_isNotRepeated() throws Exception {
    request.setMethod("CLIENTWORD");

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(fields(lines.list.get(0)))
        .containsEntry("method", RequestCorrelationFilter.OTHER_METHOD);
  }

  /** The response still reads 200 here, and the container is about to answer 500. */
  @Test
  void chainThatThrows_logsServerErrorAndStillCleansUp() {
    FilterChain chain =
        (req, res) -> {
          throw new ServletException("failed below the handler");
        };

    assertThatThrownBy(() -> filter.doFilter(request, response, chain))
        .isInstanceOf(ServletException.class);

    assertThat(returnedId()).isNotBlank();
    assertThat(fields(lines.list.get(0))).containsEntry("status", 500);
    assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
  }

  private String returnedId() {
    return Objects.requireNonNull(response.getHeader(RequestCorrelationFilter.HEADER));
  }

  private static Map<String, Object> fields(ILoggingEvent line) {
    return line.getKeyValuePairs().stream()
        .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
  }
}

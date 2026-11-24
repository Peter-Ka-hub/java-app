package dev.schooldiary.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class LoginRateLimitFilterTests {
  private final ObjectMapper json = JsonMapper.builder().build();
  private final AtomicLong now = new AtomicLong();

  @Test
  void limitsLoginAttemptsAndReturnsAReadable429AndRetryAfter() throws Exception {
    var filter = limiter(2, 10);
    assertThat(login(filter, "192.0.2.1").getStatus()).isEqualTo(200);
    assertThat(login(filter, "192.0.2.1").getStatus()).isEqualTo(200);

    var rejected = login(filter, "192.0.2.1");
    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
    var body = json.readTree(rejected.getContentAsString());
    assertThat(body.get("status").asInt()).isEqualTo(429);
    assertThat(body.get("path").asText()).isEqualTo("/api/auth/login");
  }

  @Test
  void limitsAddressesIndependentlyAndIgnoresSpoofedForwardedHeaders() throws Exception {
    var filter = limiter(1, 10);
    assertThat(login(filter, "192.0.2.1").getStatus()).isEqualTo(200);
    assertThat(login(filter, "192.0.2.2").getStatus()).isEqualTo(200);

    var request = request("POST", "/api/auth/login", "192.0.2.1");
    request.addHeader("X-Forwarded-For", "192.0.2.3");
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> res.getWriter().write("allowed"));
    assertThat(response.getStatus()).isEqualTo(429);
  }

  @Test
  void windowExpiresWithoutARequestExtendingTheBlock() throws Exception {
    var filter = limiter(1, 10);
    login(filter, "192.0.2.1");
    now.set(Duration.ofMillis(29_500).toNanos());
    assertThat(login(filter, "192.0.2.1").getHeader("Retry-After")).isEqualTo("31");

    now.set(Duration.ofSeconds(60).toNanos());
    assertThat(login(filter, "192.0.2.1").getStatus()).isEqualTo(200);
    assertThat(login(filter, "192.0.2.1").getStatus()).isEqualTo(429);
  }

  @Test
  void capacityIsBoundedAndExpiredEntriesFreeSpace() throws Exception {
    var filter = limiter(10, 2);
    assertThat(login(filter, "192.0.2.1").getStatus()).isEqualTo(200);
    assertThat(login(filter, "192.0.2.2").getStatus()).isEqualTo(200);
    assertThat(login(filter, "192.0.2.3").getStatus()).isEqualTo(429);
    assertThat(login(filter, "192.0.2.1").getStatus()).isEqualTo(200);

    now.set(Duration.ofMinutes(1).toNanos());
    assertThat(login(filter, "192.0.2.3").getStatus()).isEqualTo(200);
  }

  @Test
  void otherRequestsAreUnaffectedAndContextPathsWork() throws Exception {
    var filter = limiter(1, 10);
    login(filter, "192.0.2.1");
    for (var methodAndPath :
        List.of(
            new String[] {"GET", "/api/auth/login"},
            new String[] {"GET", "/api/auth/csrf"},
            new String[] {"POST", "/api/schools"})) {
      var response = new MockHttpServletResponse();
      filter.doFilter(
          request(methodAndPath[0], methodAndPath[1], "192.0.2.1"),
          response,
          (req, res) -> res.getWriter().write("allowed"));
      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getContentAsString()).isEqualTo("allowed");
    }
    var contextRequest = request("POST", "/diary/api/auth/login", "192.0.2.1");
    contextRequest.setContextPath("/diary");
    var response = new MockHttpServletResponse();
    filter.doFilter(contextRequest, response, (req, res) -> res.getWriter().write("allowed"));
    assertThat(response.getStatus()).isEqualTo(429);

    var encodedRequest = request("POST", "/api/%61uth/login", "192.0.2.1");
    encodedRequest.setServletPath("/api/auth/login");
    var encodedResponse = new MockHttpServletResponse();
    filter.doFilter(
        encodedRequest, encodedResponse, (req, res) -> res.getWriter().write("allowed"));
    assertThat(encodedResponse.getStatus()).isEqualTo(429);
  }

  @Test
  void concurrentAttemptsCannotBypassTheLimit() throws Exception {
    var filter = limiter(5, 10);
    List<Callable<Integer>> requests = new ArrayList<>();
    for (int i = 0; i < 32; i++) {
      requests.add(() -> login(filter, "192.0.2.1").getStatus());
    }
    List<Integer> statuses = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(8)) {
      for (var result : executor.invokeAll(requests)) {
        statuses.add(result.get());
      }
    }
    assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(5);
    assertThat(statuses.stream().filter(status -> status == 429).count()).isEqualTo(27);
  }

  @Test
  void invalidConfigurationFailsFast() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LoginRateLimitFilter(json, 0, Duration.ofMinutes(1), 10));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LoginRateLimitFilter(json, 1, Duration.ZERO, 10));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LoginRateLimitFilter(json, 1, Duration.ofMinutes(1), 0));
  }

  private LoginRateLimitFilter limiter(int maxAttempts, int maxEntries) {
    return new LoginRateLimitFilter(json, maxAttempts, Duration.ofMinutes(1), maxEntries, now::get);
  }

  private MockHttpServletResponse login(LoginRateLimitFilter filter, String address)
      throws Exception {
    var response = new MockHttpServletResponse();
    filter.doFilter(
        request("POST", "/api/auth/login", address),
        response,
        (req, res) -> res.getWriter().write("allowed"));
    return response;
  }

  private MockHttpServletRequest request(String method, String path, String address) {
    var request = new MockHttpServletRequest(method, path);
    request.setRemoteAddr(address);
    return request;
  }
}

package dev.schooldiary.platform.security;

import dev.schooldiary.platform.web.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** A bounded per-process login limit keyed by the actual remote address, not client headers. */
public class LoginRateLimitFilter extends OncePerRequestFilter {
  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  private final ObjectMapper json;
  private final int maxAttempts;
  private final long windowNanos;
  private final int maxEntries;
  private final LongSupplier ticker;
  private final Map<String, AttemptWindow> windows = new HashMap<>();
  private long lastCleanup;

  public LoginRateLimitFilter(ObjectMapper json, int maxAttempts, Duration window, int maxEntries) {
    this(json, maxAttempts, window, maxEntries, System::nanoTime);
  }

  LoginRateLimitFilter(
      ObjectMapper json, int maxAttempts, Duration window, int maxEntries, LongSupplier ticker) {
    if (maxAttempts < 1 || maxEntries < 1 || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("Login limits and window must be positive");
    }
    this.json = json;
    this.maxAttempts = maxAttempts;
    this.windowNanos = window.toNanos();
    this.maxEntries = maxEntries;
    this.ticker = ticker;
    this.lastCleanup = ticker.getAsLong();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    if (path == null || path.isEmpty()) {
      path = request.getRequestURI().substring(request.getContextPath().length());
    }
    return !"POST".equals(request.getMethod()) || !"/api/auth/login".equals(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long retryAfter = admit(request.getRemoteAddr());
    if (retryAfter == 0) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(429);
    response.setHeader("Retry-After", Long.toString(retryAfter));
    response.setContentType("application/json");
    json.writeValue(
        response.getOutputStream(),
        new ApiErrorResponse(
            Instant.now(),
            429,
            "Too Many Requests",
            "Too many login attempts. Try again later.",
            request.getRequestURI(),
            List.of()));
  }

  private synchronized long admit(String remoteAddress) {
    long now = ticker.getAsLong();
    if (now - lastCleanup >= windowNanos || windows.size() >= maxEntries) {
      windows.entrySet().removeIf(entry -> expired(entry.getValue(), now));
      lastCleanup = now;
    }
    AttemptWindow current = windows.get(remoteAddress);
    if (current != null && expired(current, now)) {
      windows.remove(remoteAddress);
      current = null;
    }
    if (current == null) {
      if (windows.size() >= maxEntries) {
        long firstRelease =
            windows.values().stream()
                .mapToLong(entry -> remainingSeconds(entry, now))
                .min()
                .orElse(1);
        return Math.max(1, firstRelease);
      }
      windows.put(remoteAddress, new AttemptWindow(now, 1));
      return 0;
    }
    if (current.attempts() >= maxAttempts) {
      return remainingSeconds(current, now);
    }
    windows.put(remoteAddress, new AttemptWindow(current.startedAt(), current.attempts() + 1));
    return 0;
  }

  private boolean expired(AttemptWindow entry, long now) {
    return now - entry.startedAt() >= windowNanos;
  }

  private long remainingSeconds(AttemptWindow entry, long now) {
    long remaining = windowNanos - (now - entry.startedAt());
    return Math.max(1, 1 + (remaining - 1) / NANOS_PER_SECOND);
  }

  private record AttemptWindow(long startedAt, int attempts) {}
}

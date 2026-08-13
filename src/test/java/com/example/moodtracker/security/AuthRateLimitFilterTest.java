package com.example.moodtracker.security;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

  @Mock private FilterChain filterChain;

  @Test
  void passesThroughRequestsToUnrelatedPaths() throws Exception {
    AuthRateLimitFilter filter = new AuthRateLimitFilter(1, Duration.ofMinutes(5));
    HttpServletRequest request = request("POST", "/moodtracker", "1.2.3.4");
    HttpServletResponse response = response();

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(429);
  }

  @Test
  void passesThroughGetRequestsToLoginPage() throws Exception {
    AuthRateLimitFilter filter = new AuthRateLimitFilter(1, Duration.ofMinutes(5));
    HttpServletRequest request = request("GET", "/login", "1.2.3.4");
    HttpServletResponse response = response();

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  void allowsUpToTheConfiguredLimitThenBlocks() throws Exception {
    AuthRateLimitFilter filter = new AuthRateLimitFilter(3, Duration.ofMinutes(5));
    String ip = "5.6.7.8";

    for (int i = 0; i < 3; i++) {
      HttpServletRequest request = request("POST", "/login", ip);
      HttpServletResponse response = response();
      filter.doFilter(request, response, filterChain);
      verify(response, never()).setStatus(429);
    }

    HttpServletRequest fourthRequest = request("POST", "/login", ip);
    HttpServletResponse fourthResponse = response();
    filter.doFilter(fourthRequest, fourthResponse, filterChain);

    verify(fourthResponse).setStatus(429);
    verify(filterChain, times(3)).doFilter(any(), any());
  }

  @Test
  void tracksDifferentPathsIndependently() throws Exception {
    AuthRateLimitFilter filter = new AuthRateLimitFilter(1, Duration.ofMinutes(5));
    String ip = "9.9.9.9";

    HttpServletRequest loginRequest = request("POST", "/login", ip);
    filter.doFilter(loginRequest, response(), filterChain);

    // The one login attempt used up /login's budget, but /register is tracked
    // separately - it should still be allowed.
    HttpServletRequest registerRequest = request("POST", "/register", ip);
    HttpServletResponse registerResponse = response();
    filter.doFilter(registerRequest, registerResponse, filterChain);

    verify(registerResponse, never()).setStatus(429);
  }

  @Test
  void tracksDifferentIpsIndependently() throws Exception {
    AuthRateLimitFilter filter = new AuthRateLimitFilter(1, Duration.ofMinutes(5));

    filter.doFilter(request("POST", "/login", "1.1.1.1"), response(), filterChain);

    HttpServletResponse secondIpResponse = response();
    filter.doFilter(request("POST", "/login", "2.2.2.2"), secondIpResponse, filterChain);

    verify(secondIpResponse, never()).setStatus(429);
  }

  @Test
  void allowsRequestsAgainAfterTheWindowExpires() throws Exception {
    AuthRateLimitFilter filter = new AuthRateLimitFilter(1, Duration.ofMillis(50));
    String ip = "3.3.3.3";

    filter.doFilter(request("POST", "/login", ip), response(), filterChain);

    Thread.sleep(100);

    HttpServletResponse laterResponse = response();
    filter.doFilter(request("POST", "/login", ip), laterResponse, filterChain);

    verify(laterResponse, never()).setStatus(429);
  }

  // The (int, long) constructor is the one Spring actually wires via @Value
  // (see playwright.config.js's webServer.env for why the e2e suite raises
  // it) - this just confirms it delegates correctly to the same behavior
  // already exercised above via the (int, Duration) constructor.
  @Test
  void publicConstructorConvertsWindowMinutesToDuration() throws Exception {
    AuthRateLimitFilter filter = new AuthRateLimitFilter(1, 5L);
    String ip = "4.4.4.4";

    filter.doFilter(request("POST", "/login", ip), response(), filterChain);

    HttpServletResponse secondResponse = response();
    filter.doFilter(request("POST", "/login", ip), secondResponse, filterChain);

    verify(secondResponse).setStatus(429);
  }

  private HttpServletRequest request(String method, String uri, String remoteAddr) {
    HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn(method);
    // Short-circuit evaluation in the filter means getRequestURI()/getRemoteAddr()
    // aren't always reached (e.g. a non-POST method skips both) - lenient so
    // pass-through tests don't trip strict stubbing over stubs they never use.
    lenient().when(request.getRequestURI()).thenReturn(uri);
    lenient().when(request.getRemoteAddr()).thenReturn(remoteAddr);
    return request;
  }

  private HttpServletResponse response() throws Exception {
    HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);
    // Only exercised when a request actually gets rate-limited - lenient so the
    // other tests (which never call getWriter()) don't trip strict stubbing.
    lenient().when(response.getWriter()).thenReturn(new PrintWriter(java.io.Writer.nullWriter()));
    return response;
  }

  private static <T> T any() {
    return org.mockito.ArgumentMatchers.any();
  }
}

package com.example.moodtracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple in-memory, per-IP rate limit on POST /login and POST /register, to blunt
 * naive automated credential-stuffing and account-enumeration attempts. Deliberately
 * basic: no new dependency, no shared state across instances - just enough to slow
 * down a single attacking source hitting a single instance. Memory use is bounded by
 * the number of distinct (path, IP) pairs seen within the window, which is an
 * accepted tradeoff for a lightweight, dependency-free defense.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/login", "/register");

    private final int maxAttempts;
    private final Duration window;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public AuthRateLimitFilter() {
        this(20, Duration.ofMinutes(5));
    }

    // Package-private: lets tests use a small window/limit instead of waiting on
    // real wall-clock time.
    AuthRateLimitFilter(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getRequestURI() + "|" + clientIp(request);
        if (isRateLimited(key)) {
            response.setStatus(429); // Too Many Requests - not in HttpServletResponse's SC_* constants
            response.setContentType("text/plain");
            response.getWriter().write("Too many attempts. Please wait a few minutes and try again.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(String key) {
        Instant cutoff = Instant.now().minus(window);
        CopyOnWriteArrayList<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        attempts.removeIf(attempt -> attempt.isBefore(cutoff));

        if (attempts.size() >= maxAttempts) {
            return true;
        }
        attempts.add(Instant.now());
        return false;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

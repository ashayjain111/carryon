package com.carryon.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding-window limiter for the credential endpoints (brute force / credential stuffing).
 * In-memory is correct for a single instance. If the API is ever scaled horizontally,
 * move this to the edge (AWS WAF rate-based rule) instead of adding Redis.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final AppProperties props;

    public RateLimitFilter(AppProperties props) { this.props = props; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        Deque<Long> window = hits.computeIfAbsent(req.getRemoteAddr(), k -> new ArrayDeque<>());
        boolean limited;
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > 60_000) window.pollFirst();
            limited = window.size() >= props.authRateLimitPerMinute();
            if (!limited) window.addLast(now);
        }
        if (limited) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too many requests, slow down\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}

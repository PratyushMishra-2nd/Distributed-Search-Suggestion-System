package com.dsss.perf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Times every API request and feeds the {@link LatencyRecorder}. Endpoints are
 * bucketed by method + path prefix so query strings don't fragment the stats.
 */
@Component
@Order(1)
public class LatencyFilter extends OncePerRequestFilter {

    private final LatencyRecorder recorder;

    public LatencyFilter(LatencyRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long micros = (System.nanoTime() - start) / 1000;
            recorder.record(req.getMethod() + " " + bucket(req.getRequestURI()), micros);
        }
    }

    private String bucket(String uri) {
        int second = uri.indexOf('/', 1);
        return second > 0 ? uri.substring(0, second) : uri;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/");
    }
}

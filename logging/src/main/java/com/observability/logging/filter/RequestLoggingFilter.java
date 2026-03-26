package com.observability.logging.filter;

import com.observability.logging.publisher.EventLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(2)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final EventLogger eventLogger;

    public RequestLoggingFilter(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            // Always record timing — even if the handler threw an exception
            long duration = System.currentTimeMillis() - start;
            String path   = request.getRequestURI();

            // Skip actuator health probes — they'd drown the event stream
            if (!path.startsWith("/actuator")) {
                eventLogger.request(request.getMethod(), path,
                                    response.getStatus(), duration);
            }
        }
    }
}
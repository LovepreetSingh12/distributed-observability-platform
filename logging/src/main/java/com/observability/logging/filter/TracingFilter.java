package com.observability.logging.filter;

import com.observability.logging.model.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(1)  // must run before RequestLoggingFilter
public class TracingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {
        try {
            // Accept an incoming traceId (from an upstream service) or generate one
            String traceId = request.getHeader(TraceContext.TRACE_HEADER);
            String spanId  = request.getHeader(TraceContext.SPAN_HEADER);

            TraceContext.setTraceId(traceId != null ? traceId : TraceContext.generateTraceId());
            TraceContext.setSpanId (spanId  != null ? spanId  : TraceContext.generateSpanId());

            // Echo the IDs back so the caller can correlate their own logs
            response.setHeader(TraceContext.TRACE_HEADER, TraceContext.getTraceId());
            response.setHeader(TraceContext.SPAN_HEADER,  TraceContext.getSpanId());

            chain.doFilter(request, response);

        } finally {
            // Always clear — prevents one request's IDs leaking into the next
            // request on the same thread (common with thread pools)
            TraceContext.clear();
        }
    }
}

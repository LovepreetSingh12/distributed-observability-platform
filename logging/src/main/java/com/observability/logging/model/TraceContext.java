package com.observability.logging.model;

import org.slf4j.MDC;
import java.util.UUID;

/**
 * Static utility for trace ID propagation via SLF4J MDC.
 *
 * MDC is thread-local, so trace IDs are automatically isolated per request
 * thread. The TracingFilter sets them at the start of each HTTP request;
 * this class provides the read/write API everywhere else.
 */
public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY  = "spanId";

    // HTTP header names for cross-service propagation
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String SPAN_HEADER  = "X-Span-Id";

    private TraceContext() {}

    public static String generateTraceId() {
        // 16-char hex string — compact but unique enough for distributed tracing
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static void setTraceId(String traceId) { MDC.put(TRACE_ID_KEY, traceId); }
    public static void setSpanId(String spanId)   { MDC.put(SPAN_ID_KEY,  spanId); }

    public static String getTraceId() {
        String id = MDC.get(TRACE_ID_KEY);
        return id != null ? id : generateTraceId();
    }

    public static String getSpanId() {
        String id = MDC.get(SPAN_ID_KEY);
        return id != null ? id : generateSpanId();
    }

    /** Sets both IDs only if they are not already present. */
    public static void initIfAbsent() {
        if (MDC.get(TRACE_ID_KEY) == null) MDC.put(TRACE_ID_KEY, generateTraceId());
        if (MDC.get(SPAN_ID_KEY)  == null) MDC.put(SPAN_ID_KEY,  generateSpanId());
    }

    /** MUST be called at the end of every request — prevents MDC leakage in thread pools. */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
    }
}
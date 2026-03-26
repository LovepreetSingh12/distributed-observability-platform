package com.observability.logging.publisher;

import com.observability.logging.model.LogEvent;
import com.observability.logging.model.LogEvent.EventType;
import com.observability.logging.model.LogEvent.Level;
import com.observability.logging.model.TraceContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.Map;

/**
 * Primary SDK API. Services inject this as a Spring bean and call it directly.
 *
 * Examples:
 *   eventLogger.info("Order created", Map.of("orderId", id));
 *   eventLogger.error("Payment failed", exception);
 *   eventLogger.request("POST", "/api/orders", 201, 45L);
 */
public class EventLogger {

    private final KafkaEventPublisher publisher;
    private final String              serviceName;
    private final String              serviceVersion;
    private final String              hostName;

    public EventLogger(KafkaEventPublisher publisher,
                       String serviceName,
                       String serviceVersion) {
        this.publisher      = publisher;
        this.serviceName    = serviceName;
        this.serviceVersion = serviceVersion;
        this.hostName       = resolveHostName();
    }

    // ── Plain log methods ─────────────────────────────────────────────────────

    public void trace(String msg)                            { emit(Level.TRACE, msg, null); }
    public void debug(String msg)                            { emit(Level.DEBUG, msg, null); }
    public void info(String msg)                             { emit(Level.INFO,  msg, null); }
    public void warn(String msg)                             { emit(Level.WARN,  msg, null); }
    public void error(String msg)                            { emit(Level.ERROR, msg, null); }

    public void debug(String msg, Map<String,Object> meta)   { emit(Level.DEBUG, msg, meta); }
    public void info(String msg,  Map<String,Object> meta)   { emit(Level.INFO,  msg, meta); }
    public void warn(String msg,  Map<String,Object> meta)   { emit(Level.WARN,  msg, meta); }
    public void error(String msg, Map<String,Object> meta)   { emit(Level.ERROR, msg, meta); }

    // ── Exception events ──────────────────────────────────────────────────────

    public void error(String message, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));

        LogEvent event = base(Level.ERROR, EventType.EXCEPTION)
            .message(message)
            .exceptionClass(throwable.getClass().getName())
            .exceptionMessage(throwable.getMessage())
            .stackTrace(sw.toString())
            .build();

        publisher.publish(event);
    }

    // ── HTTP request events ───────────────────────────────────────────────────

    public void request(String method, String path, int status, long durationMs) {
        LogEvent event = base(Level.INFO, EventType.REQUEST)
            .message(method + " " + path + " → " + status)
            .httpMethod(method)
            .httpPath(path)
            .httpStatus(status)
            .durationMs(durationMs)
            .build();

        publisher.publish(event);
    }

    // ── Metric events ─────────────────────────────────────────────────────────

    public void metric(String name, Object value) {
        emit(Level.INFO, EventType.METRIC, name, Map.of("value", value));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void emit(Level level, String msg, Map<String,Object> meta) {
        emit(level, EventType.LOG, msg, meta);
    }

    private void emit(Level level, EventType type, String msg, Map<String,Object> meta) {
        publisher.publish(
            base(level, type).message(msg).metadata(meta).build()
        );
    }

    private LogEvent.Builder base(Level level, EventType type) {
        return LogEvent.builder()
            .serviceName(serviceName)
            .serviceVersion(serviceVersion)
            .hostName(hostName)
            .level(level)
            .eventType(type)
            .traceId(TraceContext.getTraceId())
            .spanId(TraceContext.getSpanId())
            .loggerName(serviceName);
    }

    private String resolveHostName() {
        try   { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown"; }
    }
}
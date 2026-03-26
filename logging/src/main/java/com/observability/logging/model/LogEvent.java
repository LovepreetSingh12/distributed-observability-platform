package com.observability.logging.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEvent {
    public enum Level     { TRACE, DEBUG, INFO, WARN, ERROR, FATAL }
    public enum EventType { LOG, REQUEST, EXCEPTION, METRIC, CUSTOM }

    // ── Identity ──────────────────────────────────────────────────────────────
    private String eventId;       // UUID, unique per event
    private String traceId;       // shared across all events in one request
    private String spanId;        // unique per service hop

    // ── Origin ────────────────────────────────────────────────────────────────
    private String serviceName;
    private String serviceVersion;
    private String hostName;
    private String loggerName;
    private String threadName;

    // ── Classification ────────────────────────────────────────────────────────
    private Level     level;
    private EventType eventType;
    private String    message;
    private Instant   timestamp;

    // ── HTTP fields (REQUEST events only) ─────────────────────────────────────
    private String  httpMethod;
    private String  httpPath;
    private Integer httpStatus;
    private Long    durationMs;

    // ── Exception fields (EXCEPTION events only) ──────────────────────────────
    private String exceptionClass;
    private String exceptionMessage;
    private String stackTrace;

    // ── Freeform metadata ─────────────────────────────────────────────────────
    private Map<String, Object> metadata;
    private LogEvent() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final LogEvent e = new LogEvent();

        public Builder() {
            // Auto-populate mandatory fields so callers can never forget them
            e.eventId    = UUID.randomUUID().toString();
            e.timestamp  = Instant.now();
            e.threadName = Thread.currentThread().getName();
        }

        public Builder traceId(String v)          { e.traceId = v;         return this; }
        public Builder spanId(String v)           { e.spanId = v;          return this; }
        public Builder serviceName(String v)      { e.serviceName = v;     return this; }
        public Builder serviceVersion(String v)   { e.serviceVersion = v;  return this; }
        public Builder hostName(String v)         { e.hostName = v;        return this; }
        public Builder loggerName(String v)       { e.loggerName = v;      return this; }
        public Builder level(Level v)             { e.level = v;           return this; }
        public Builder eventType(EventType v)     { e.eventType = v;       return this; }
        public Builder message(String v)          { e.message = v;         return this; }
        public Builder httpMethod(String v)       { e.httpMethod = v;      return this; }
        public Builder httpPath(String v)         { e.httpPath = v;        return this; }
        public Builder httpStatus(Integer v)      { e.httpStatus = v;      return this; }
        public Builder durationMs(Long v)         { e.durationMs = v;      return this; }
        public Builder exceptionClass(String v)   { e.exceptionClass = v;  return this; }
        public Builder exceptionMessage(String v) { e.exceptionMessage = v;return this; }
        public Builder stackTrace(String v)       { e.stackTrace = v;      return this; }
        public Builder metadata(Map<String,Object> v){ e.metadata = v;     return this; }
        public LogEvent build()                   { return e; }
    }

    // ── Getters (no setters — immutable after build()) ────────────────────────
    public String getEventId()          { return eventId; }
    public String getTraceId()          { return traceId; }
    public String getSpanId()           { return spanId; }
    public String getServiceName()      { return serviceName; }
    public String getServiceVersion()   { return serviceVersion; }
    public String getHostName()         { return hostName; }
    public String getLoggerName()       { return loggerName; }
    public String getThreadName()       { return threadName; }
    public Level getLevel()             { return level; }
    public EventType getEventType()     { return eventType; }
    public String getMessage()          { return message; }
    public Instant getTimestamp()       { return timestamp; }
    public String getHttpMethod()       { return httpMethod; }
    public String getHttpPath()         { return httpPath; }
    public Integer getHttpStatus()      { return httpStatus; }
    public Long getDurationMs()         { return durationMs; }
    public String getExceptionClass()   { return exceptionClass; }
    public String getExceptionMessage() { return exceptionMessage; }
    public String getStackTrace()       { return stackTrace; }
    public Map<String, Object> getMetadata() { return metadata; }
}

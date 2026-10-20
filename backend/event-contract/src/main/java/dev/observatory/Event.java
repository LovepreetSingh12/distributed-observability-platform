package dev.observatory;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record Event(
    String eventId,
    int baseSchemaVersion,
    String eventType,
    int payloadSchemaVersion,
    Instant occurredAt,
    String applicationId,
    String environment,
    String level,
    String message,
    String traceId,
    String spanId,
    JsonNode payload) {}

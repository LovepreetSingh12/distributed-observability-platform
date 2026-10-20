package dev.observatory;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;

public final class EnvelopeValidator {
  private static final Set<String> FIELDS =
      Set.of(
          "eventId",
          "baseSchemaVersion",
          "eventType",
          "payloadSchemaVersion",
          "occurredAt",
          "applicationId",
          "environment",
          "level",
          "message",
          "traceId",
          "spanId",
          "payload");

  public static Event validate(JsonNode n, ObjectMapper mapper, String app) {
    if (n == null || !n.isObject()) throw new IllegalArgumentException("Event must be an object");
    n.fieldNames()
        .forEachRemaining(
            k -> {
              if (!FIELDS.contains(k))
                throw new IllegalArgumentException("Unknown envelope field: " + k);
            });
    for (String field :
        List.of(
            "eventId",
            "eventType",
            "occurredAt",
            "applicationId",
            "environment",
            "level",
            "message")) {
      if (!n.path(field).isTextual())
        throw new IllegalArgumentException(field + " must be a string");
    }
    for (String field : List.of("traceId", "spanId")) {
      if (n.hasNonNull(field) && !n.path(field).isTextual())
        throw new IllegalArgumentException(field + " must be a string");
    }
    for (String field : List.of("baseSchemaVersion", "payloadSchemaVersion")) {
      if (!n.path(field).isIntegralNumber() || !n.path(field).canConvertToInt())
        throw new IllegalArgumentException(field + " must be an integer");
    }
    Event e;
    try {
      e = mapper.treeToValue(n, Event.class);
    } catch (Exception x) {
      throw new IllegalArgumentException("Malformed envelope");
    }
    if (e.eventId() == null || !UUID.fromString(e.eventId()).toString().equals(e.eventId()))
      throw new IllegalArgumentException("eventId must be canonical UUID");
    if (e.baseSchemaVersion() != 1 || e.payloadSchemaVersion() < 1)
      throw new IllegalArgumentException("Unsupported schema version");
    if (!app.equals(e.applicationId()))
      throw new IllegalArgumentException("Application identity mismatch");
    bounded(e.applicationId(), 100);
    bounded(e.environment(), 64);
    bounded(e.eventType(), 100);
    if (!e.eventType().matches("[A-Z][A-Z0-9_]{0,99}"))
      throw new IllegalArgumentException("Invalid eventType");
    bounded(e.message(), 4096);
    if (e.level() == null || !Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR").contains(e.level()))
      throw new IllegalArgumentException("Invalid level");
    if (e.traceId() != null) bounded(e.traceId(), 128);
    if (e.spanId() != null) bounded(e.spanId(), 128);
    if (e.occurredAt() == null
        || e.occurredAt().isBefore(Instant.now().minus(Duration.ofDays(7)))
        || e.occurredAt().isAfter(Instant.now().plusSeconds(300)))
      throw new IllegalArgumentException("occurredAt outside allowed window");
    if (e.payload() == null
        || !e.payload().isObject()
        || e.payload().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 32768
        || depth(e.payload()) > 12)
      throw new IllegalArgumentException("Invalid payload size, type or depth");
    return e;
  }

  public static int depth(JsonNode n) {
    int d = 0;
    for (JsonNode c : n) d = Math.max(d, depth(c));
    return d + 1;
  }

  public static void bounded(String s, int max) {
    if (s == null || s.isBlank() || s.length() > max)
      throw new IllegalArgumentException("Missing or oversized field");
  }
}

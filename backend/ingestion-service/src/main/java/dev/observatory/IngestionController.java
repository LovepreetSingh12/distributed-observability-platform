package dev.observatory;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class IngestionController {
  private final MongoTemplate db;
  private final ObjectMapper mapper;
  private final SchemaRegistry schemas;
  private final KafkaTemplate<String, String> kafka;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final int rate, batchLimit, retention;

  static class Window {
    long second;
    int count;

    synchronized boolean allow(int n, int limit) {
      long now = System.currentTimeMillis() / 1000;
      if (second != now) {
        second = now;
        count = 0;
      }
      if (count + n > limit) return false;
      count += n;
      return true;
    }
  }

  public IngestionController(
      MongoTemplate db,
      ObjectMapper mapper,
      SchemaRegistry schemas,
      KafkaTemplate<String, String> kafka,
      @Value("${ingestion.rate:500}") int rate,
      @Value("${ingestion.batch-limit:100}") int batchLimit,
      @Value("${ingestion.retention-days:14}") int retention) {
    this.db = db;
    this.mapper = mapper;
    this.schemas = schemas;
    this.kafka = kafka;
    this.rate = rate;
    this.batchLimit = batchLimit;
    this.retention = retention;
  }

  @PostMapping("/api/v1/events/batch")
  public ResponseEntity<?> ingest(
      @RequestHeader(value = "Authorization", defaultValue = "") String authorization,
      @RequestBody JsonNode batch) {
    if (!authorization.startsWith("Bearer ")) return ResponseEntity.status(401).build();
    Document key =
        db.findOne(
            Query.query(
                Criteria.where("hash")
                    .is(Secrets.hash(authorization.substring(7)))
                    .and("revoked")
                    .is(false)),
            Document.class,
            "api_keys");
    if (key == null) return ResponseEntity.status(401).build();
    if (!batch.isArray()
        || batch.size() < 1
        || batch.size() > batchLimit
        || batch.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 524288)
      return ResponseEntity.status(413).body(Map.of("error", "Batch exceeds limits"));
    String tenant = key.getString("tenantId"), app = key.getString("applicationId");
    if (windows.size() > 10000)
      windows
          .entrySet()
          .removeIf(e -> e.getValue().second < System.currentTimeMillis() / 1000 - 60);
    if (!windows.computeIfAbsent(tenant + ":" + app, k -> new Window()).allow(batch.size(), rate))
      return ResponseEntity.status(429)
          .header("Retry-After", "1")
          .body(Map.of("error", "Application rate limit"));
    List<BatchResult> results = new ArrayList<>();
    for (JsonNode n : batch) {
      String id = n.path("eventId").asText("");
      try {
        Event e = EnvelopeValidator.validate(n, mapper, app);
        List<String> errors =
            schemas.validate(
                schemas.published(tenant, e.eventType(), e.payloadSchemaVersion()), e.payload());
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        var stored = mapper.valueToTree(e);
        ((com.fasterxml.jackson.databind.node.ObjectNode) stored)
            .put("tenantId", tenant)
            .put("receivedAt", Instant.now().toString())
            .put("expiresAt", Instant.now().plus(Duration.ofDays(retention)).toString());
        String partition =
            tenant + ":" + app + ":" + (e.traceId() == null ? e.eventId() : e.traceId());
        kafka
            .send("events.ingested.v1", partition, mapper.writeValueAsString(stored))
            .get(12, TimeUnit.SECONDS);
        results.add(new BatchResult(id, "accepted", null));
      } catch (IllegalArgumentException e) {
        results.add(new BatchResult(id, "rejected", e.getMessage()));
      } catch (Exception e) {
        results.add(
            new BatchResult(id, "retryable", "Publication or validation dependency unavailable"));
      }
    }
    // Diagnostics are best effort and cannot change an acknowledged delivery outcome.
    try {
      Update counters = new Update().set("lastIngestionAt", new Date());
      for (String status : List.of("accepted", "rejected", "retryable")) {
        counters.inc(
            "ingestion." + status, results.stream().filter(r -> status.equals(r.status())).count());
      }
      results.stream()
          .filter(r -> !"accepted".equals(r.status()))
          .reduce((a, b) -> b)
          .ifPresent(
              r ->
                  counters
                      .set("lastIngestionError", r.reason())
                      .set("lastIngestionErrorAt", new Date()));
      db.updateFirst(
          Query.query(Criteria.where("tenantId").is(tenant).and("applicationId").is(app)),
          counters,
          "applications");
    } catch (Exception ignored) {
      // Delivery has already been decided; metrics must not cause retries or log event payloads.
    }
    return ResponseEntity.ok(results);
  }
}

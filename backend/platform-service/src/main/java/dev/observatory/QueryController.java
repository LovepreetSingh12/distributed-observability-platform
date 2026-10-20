package dev.observatory;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class QueryController {
  private final MongoTemplate db;
  private final ObjectMapper mapper;

  public QueryController(MongoTemplate db, ObjectMapper mapper) {
    this.db = db;
    this.mapper = mapper;
  }

  public record Filter(String field, String op, JsonNode value) {}

  public record Request(
      Instant from,
      Instant to,
      String applicationId,
      String environment,
      String level,
      String eventType,
      Integer schemaVersion,
      String traceId,
      List<Filter> filters,
      String cursor,
      Integer limit,
      String metric,
      String field,
      Integer bucketMinutes) {}

  Criteria criteria(Request r) {
    if (r.from() == null
        || r.to() == null
        || !r.to().isAfter(r.from())
        || Duration.between(r.from(), r.to()).compareTo(Duration.ofDays(30)) > 0)
      throw new IllegalArgumentException("Time range must be positive and at most 30 days");
    List<Criteria> cs = new ArrayList<>();
    cs.add(Criteria.where("tenantId").is(UserScope.tenant()));
    cs.add(Criteria.where("occurredAt").gte(Date.from(r.from())).lt(Date.from(r.to())));
    String[] fields = {"applicationId", "environment", "level", "eventType", "traceId"};
    String[] values = {r.applicationId(), r.environment(), r.level(), r.eventType(), r.traceId()};
    for (int i = 0; i < fields.length; i++)
      if (values[i] != null && !values[i].isBlank()) {
        EnvelopeValidator.bounded(values[i], 128);
        cs.add(Criteria.where(fields[i]).is(values[i]));
      }
    if (r.schemaVersion() != null)
      cs.add(Criteria.where("payloadSchemaVersion").is(r.schemaVersion()));
    if (r.filters() != null) {
      if (r.filters().size() > 5) throw new IllegalArgumentException("At most 5 payload filters");
      for (Filter f : r.filters()) {
        if (f == null || f.field() == null || f.op() == null)
          throw new IllegalArgumentException("Filter field and operator are required");
        String type = allowedPayload(r, f.field());
        if (!Set.of("eq", "gt", "gte", "lt", "lte").contains(f.op())
            || f.value() == null
            || !f.value().isValueNode()) throw new IllegalArgumentException("Unsupported filter");
        boolean numeric = Set.of("integer", "number").contains(type);
        if ((!f.op().equals("eq") && !numeric)
            || (numeric && !f.value().isNumber())
            || ("string".equals(type) && !f.value().isTextual())
            || ("boolean".equals(type) && !f.value().isBoolean()))
          throw new IllegalArgumentException("Filter type mismatch");
        Object value =
            numeric ? f.value().numberValue() : mapper.convertValue(f.value(), Object.class);
        Criteria c = Criteria.where(f.field());
        switch (f.op()) {
          case "eq" -> c.is(value);
          case "gt" -> c.gt(value);
          case "gte" -> c.gte(value);
          case "lt" -> c.lt(value);
          case "lte" -> c.lte(value);
        }
        cs.add(c);
      }
    }
    return new Criteria().andOperator(cs);
  }

  String allowedPayload(Request r, String field) {
    if (field == null
        || !field.matches("payload\\.[A-Za-z][A-Za-z0-9_]{0,63}")
        || r.eventType() == null
        || r.schemaVersion() == null)
      throw new IllegalArgumentException("Payload fields require eventType and schemaVersion");
    Document d =
        db.findOne(
            Query.query(
                Criteria.where("tenantId")
                    .is(UserScope.tenant())
                    .and("eventType")
                    .is(r.eventType())
                    .and("version")
                    .is(r.schemaVersion())
                    .and("published")
                    .is(true)),
            Document.class,
            "event_schemas");
    if (d == null) throw new IllegalArgumentException("Unknown schema");
    try {
      JsonNode p =
          mapper.readTree(d.getString("schema")).path("properties").path(field.substring(8));
      if (!p.path("x-queryable").asBoolean()
          || !Set.of("string", "integer", "number", "boolean").contains(p.path("type").asText()))
        throw new IllegalArgumentException("Field is not queryable");
      return p.path("type").asText();
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Invalid schema");
    }
  }

  @PostMapping("/api/query/events")
  public Map<String, ?> events(@RequestBody Request r) {
    Criteria c = criteria(r);
    int limit = r.limit() == null ? 50 : r.limit();
    if (limit < 1 || limit > 200) throw new IllegalArgumentException("Limit must be 1..200");
    if (r.cursor() != null) {
      try {
        String[] parts =
            new String(
                    Base64.getUrlDecoder().decode(r.cursor()),
                    java.nio.charset.StandardCharsets.UTF_8)
                .split("\\|", -1);
        Instant time = Instant.parse(parts[0]);
        UUID.fromString(parts[1]);
        c =
            new Criteria()
                .andOperator(
                    c,
                    new Criteria()
                        .orOperator(
                            Criteria.where("occurredAt").lt(Date.from(time)),
                            Criteria.where("occurredAt")
                                .is(Date.from(time))
                                .and("eventId")
                                .lt(parts[1])));
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid cursor");
      }
    }
    Query q =
        Query.query(c)
            .with(Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("eventId")))
            .limit(limit + 1)
            .maxTime(Duration.ofSeconds(3));
    List<Document> rows = db.find(q, Document.class, "events");
    boolean more = rows.size() > limit;
    if (more) rows = new ArrayList<>(rows.subList(0, limit));
    String next = "";
    if (more) {
      Document last = rows.getLast();
      next =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  (last.getDate("occurredAt").toInstant() + "|" + last.getString("eventId"))
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    return Map.of("events", ManagementController.clean(rows), "nextCursor", next);
  }

  @PostMapping("/api/query/aggregate")
  public List<Document> aggregate(@RequestBody Request r) {
    Criteria c = criteria(r);
    String metric = r.metric() == null ? "count" : r.metric();
    if (!Set.of("count", "timeseries", "group", "sum", "avg").contains(metric))
      throw new IllegalArgumentException("Unsupported metric");
    Object group = null;
    Document accumulator = new Document("$sum", 1);
    if (metric.equals("timeseries")) {
      int bucket = r.bucketMinutes() == null ? 5 : r.bucketMinutes();
      if (bucket < 1
          || bucket > 1440
          || Duration.between(r.from(), r.to()).toMinutes() / bucket > 500)
        throw new IllegalArgumentException("Use 1..1440 minute buckets with at most 500 buckets");
      group =
          new Document(
              "$dateTrunc",
              new Document("date", "$occurredAt")
                  .append("unit", "minute")
                  .append("binSize", bucket));
    }
    if (metric.equals("group")) {
      if (!Set.of("applicationId", "environment", "level", "eventType")
          .contains(r.field() == null ? "" : r.field())) allowedPayload(r, r.field());
      group = "$" + r.field();
    }
    if (metric.equals("sum") || metric.equals("avg")) {
      if (!Set.of("number", "integer").contains(allowedPayload(r, r.field())))
        throw new IllegalArgumentException("Numeric field required");
      accumulator = new Document("$" + metric, "$" + r.field());
    }
    List<Document> pipeline =
        List.of(
            new Document("$match", c.getCriteriaObject()),
            new Document("$group", new Document("_id", group).append("value", accumulator)),
            new Document(
                "$sort",
                new Document(
                    metric.equals("timeseries") ? "_id" : "value",
                    metric.equals("timeseries") ? 1 : -1)),
            new Document("$limit", metric.equals("timeseries") ? 501 : 51));
    List<Document> rows =
        db.getCollection("events")
            .aggregate(pipeline)
            .maxTime(3, TimeUnit.SECONDS)
            .allowDiskUse(false)
            .into(new ArrayList<>());
    if (rows.size() > (metric.equals("timeseries") ? 500 : 50))
      throw new IllegalArgumentException("Grouping exceeds cardinality limit; narrow the query");
    return rows;
  }
}

package dev.observatory;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ManagementController {
  private final MongoTemplate db;
  private final SchemaRegistry schemas;
  private final ObjectMapper mapper;

  public ManagementController(MongoTemplate db, ObjectMapper mapper) {
    this.db = db;
    this.mapper = mapper;
    schemas = new SchemaRegistry(db, mapper);
  }

  static Criteria scope() {
    return Criteria.where("tenantId").is(UserScope.tenant());
  }

  static Document clean(Document d) {
    if (d != null) {
      d.remove("_id");
      d.remove("hash");
    }
    return d;
  }

  static List<Document> clean(List<Document> d) {
    return d.stream().map(ManagementController::clean).toList();
  }

  @GetMapping("/api/applications")
  public List<Document> apps() {
    return clean(db.find(Query.query(scope()), Document.class, "applications"));
  }

  public record App(String applicationId, String name) {}

  @PostMapping("/api/applications")
  public Document app(@RequestBody App a) {
    if (a.applicationId() == null || !a.applicationId().matches("[a-z0-9-]{1,100}"))
      throw new IllegalArgumentException("Use a lowercase application ID");
    EnvelopeValidator.bounded(a.name(), 100);
    return clean(
        db.insert(
            new Document("tenantId", UserScope.tenant())
                .append("applicationId", a.applicationId())
                .append("name", a.name()),
            "applications"));
  }

  @GetMapping("/api/applications/{app}/keys")
  public List<Document> keys(@PathVariable String app) {
    return clean(
        db.find(Query.query(scope().and("applicationId").is(app)), Document.class, "api_keys"));
  }

  @PostMapping("/api/applications/{app}/keys")
  public Map<String, String> key(@PathVariable String app) {
    if (!db.exists(Query.query(scope().and("applicationId").is(app)), "applications"))
      throw new IllegalArgumentException("Application not found");
    String raw = Secrets.generate(), id = UUID.randomUUID().toString();
    db.insert(
        new Document("keyId", id)
            .append("tenantId", UserScope.tenant())
            .append("applicationId", app)
            .append("hash", Secrets.hash(raw))
            .append("createdAt", new Date())
            .append("revoked", false),
        "api_keys");
    return Map.of("keyId", id, "apiKey", raw);
  }

  @DeleteMapping("/api/applications/{app}/keys/{id}")
  public void revoke(@PathVariable String app, @PathVariable String id) {
    db.updateFirst(
        Query.query(scope().and("applicationId").is(app).and("keyId").is(id)),
        Update.update("revoked", true),
        "api_keys");
  }

  @GetMapping("/api/schemas")
  public List<Document> schemas() {
    return clean(
        db.find(
            Query.query(scope())
                .with(org.springframework.data.domain.Sort.by("eventType", "version")),
            Document.class,
            "event_schemas"));
  }

  public record Draft(String eventType, int version, JsonNode schema) {}

  @PostMapping("/api/schemas")
  public Document draft(@RequestBody Draft d) {
    if (d.eventType() == null || !d.eventType().matches("[A-Z][A-Z0-9_]{0,99}") || d.version() < 1)
      throw new IllegalArgumentException("Invalid schema identity");
    check(d.schema());
    return clean(
        db.insert(
            new Document("tenantId", UserScope.tenant())
                .append("eventType", d.eventType())
                .append("version", d.version())
                .append("published", false)
                .append("schema", d.schema().toString()),
            "event_schemas"));
  }

  @PutMapping("/api/schemas/{type}/{version}")
  public void update(
      @PathVariable String type, @PathVariable int version, @RequestBody JsonNode schema) {
    check(schema);
    if (db.updateFirst(
                Query.query(
                    scope()
                        .and("eventType")
                        .is(type)
                        .and("version")
                        .is(version)
                        .and("published")
                        .is(false)),
                Update.update("schema", schema.toString()),
                "event_schemas")
            .getMatchedCount()
        != 1)
      throw new IllegalArgumentException("Draft not found; published schemas are immutable");
  }

  void check(JsonNode schema) {
    if (schema == null
        || schema.toString().length() > 32768
        || EnvelopeValidator.depth(schema) > 16)
      throw new IllegalArgumentException("Schema too large or deep");
    schemas.compile(schema);
  }

  @PostMapping("/api/schemas/{type}/{version}/publish")
  public void publish(@PathVariable String type, @PathVariable int version) {
    Document d = findSchema(type, version);
    if (d.getBoolean("published", false)) return;
    try {
      check(mapper.readTree(d.getString("schema")));
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Invalid schema");
    }
    if (db.updateFirst(
                Query.query(
                    scope()
                        .and("eventType")
                        .is(type)
                        .and("version")
                        .is(version)
                        .and("published")
                        .is(false)
                        .and("schema")
                        .is(d.getString("schema"))),
                Update.update("published", true),
                "event_schemas")
            .getModifiedCount()
        != 1)
      throw new IllegalArgumentException("Draft changed while publishing; reload and retry");
  }

  Document findSchema(String type, int version) {
    Document d =
        db.findOne(
            Query.query(scope().and("eventType").is(type).and("version").is(version)),
            Document.class,
            "event_schemas");
    if (d == null) throw new IllegalArgumentException("Schema not found");
    return d;
  }

  @PostMapping("/api/schemas/{type}/{version}/validate")
  public Map<String, ?> validate(
      @PathVariable String type, @PathVariable int version, @RequestBody JsonNode payload)
      throws Exception {
    if (payload.toString().length() > 32768)
      throw new IllegalArgumentException("Payload too large");
    List<String> errors =
        schemas.validate(
            schemas.compile(mapper.readTree(findSchema(type, version).getString("schema"))),
            payload);
    return Map.of("valid", errors.isEmpty(), "errors", errors);
  }

  @GetMapping("/api/dashboards")
  public List<Document> dashboards() {
    return clean(db.find(Query.query(scope()), Document.class, "dashboards"));
  }

  @PutMapping("/api/dashboards/{id}")
  public Document dashboard(@PathVariable String id, @RequestBody JsonNode body) {
    UUID.fromString(id);
    if (body.toString().length() > 65536
        || !body.path("title").isTextual()
        || body.path("title").asText().isBlank()
        || body.path("title").asText().length() > 100
        || !body.path("panels").isArray()
        || body.path("panels").size() > 12
        || body.path("refreshSeconds").asInt() < 5
        || body.path("timeRangeMinutes").asInt() < 1
        || body.path("timeRangeMinutes").asInt() > 43200)
      throw new IllegalArgumentException("Invalid dashboard definition");
    Set<String> types = Set.of("logs", "timeseries", "count", "bar", "sum", "avg");
    for (JsonNode p : body.path("panels")) {
      if (!types.contains(p.path("type").asText())
          || !p.path("query").isObject()
          || p.path("w").asInt() < 1
          || p.path("w").asInt() > 12
          || p.path("h").asInt() < 1
          || p.path("h").asInt() > 6
          || p.path("x").asInt() < 0
          || p.path("y").asInt() < 0) throw new IllegalArgumentException("Invalid panel");
    }
    Document d =
        new Document("tenantId", UserScope.tenant())
            .append("dashboardId", id)
            .append("definition", Document.parse(body.toString()));
    db.upsert(
        Query.query(scope().and("dashboardId").is(id)),
        Update.update("definition", d.get("definition"))
            .setOnInsert("tenantId", UserScope.tenant())
            .setOnInsert("dashboardId", id),
        "dashboards");
    return d;
  }

  @DeleteMapping("/api/dashboards/{id}")
  public void delete(@PathVariable String id) {
    db.remove(Query.query(scope().and("dashboardId").is(id)), "dashboards");
  }
}

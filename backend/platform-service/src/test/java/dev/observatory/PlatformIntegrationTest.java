package dev.observatory;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.*;
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "demo.seed=true",
      "DEMO_PASSWORD=integration-password-long",
      "DEMO_API_KEY=integration-test-api-key-at-least-32"
    })
class PlatformIntegrationTest {
  @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0.15");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
  }

  @Autowired TestRestTemplate http;
  @Autowired MongoTemplate db;

  String login(String email) {
    var r =
        http.postForEntity(
            "/api/auth/login",
            Map.of("email", email, "password", "integration-password-long"),
            Map.class);
    assertEquals(200, r.getStatusCode().value());
    return (String) r.getBody().get("token");
  }

  ResponseEntity<Map> post(String path, Object body, String token) {
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth(token);
    return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
  }

  Map<String, Object> query() {
    return new HashMap<>(
        Map.of(
            "from",
            Instant.now().minusSeconds(3600).toString(),
            "to",
            Instant.now().plusSeconds(60).toString()));
  }

  @Test
  void tenantScopeAndViewerPermissions() {
    String admin = login("demo-admin@localhost"),
        other = login("other-admin@localhost"),
        viewer = login("demo-viewer@localhost");
    String id = UUID.randomUUID().toString();
    db.insert(
        new Document("tenantId", "demo")
            .append("eventId", id)
            .append("occurredAt", new Date())
            .append("message", "private"),
        "events");
    var d = post("/api/query/events", query(), admin);
    assertTrue(d.getBody().toString().contains(id));
    var o = post("/api/query/events", query(), other);
    assertFalse(o.getBody().toString().contains(id));
    assertEquals(
        403,
        post("/api/applications", Map.of("applicationId", "forbidden", "name", "No"), viewer)
            .getStatusCode()
            .value());
    assertEquals(
        401,
        http.postForEntity(
                "/api/auth/login",
                Map.of(
                    "email",
                    "demo-admin@localhost",
                    "password",
                    "integration-password-long",
                    "tenant",
                    "other"),
                Map.class)
            .getStatusCode()
            .value());
  }

  @Test
  void queryRestrictionsAndStableCursor() {
    String token = login("demo-admin@localhost");
    Date same = new Date(System.currentTimeMillis() - 5000);
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      String id = UUID.randomUUID().toString();
      ids.add(id);
      db.insert(
          new Document("tenantId", "demo")
              .append("eventId", id)
              .append("applicationId", "cursor-test")
              .append("occurredAt", same),
          "events");
    }
    Map<String, Object> q = query();
    q.put("applicationId", "cursor-test");
    q.put("limit", 2);
    var first = post("/api/query/events", q, token).getBody();
    q.put("cursor", first.get("nextCursor"));
    var second = post("/api/query/events", q, token).getBody();
    assertEquals(2, ((List<?>) first.get("events")).size());
    assertEquals(1, ((List<?>) second.get("events")).size());
    assertNotEquals(
        ((Map) ((List) first.get("events")).get(1)).get("eventId"),
        ((Map) ((List) second.get("events")).get(0)).get("eventId"));
    q.remove("cursor");
    q.put("tenantId", "other");
    assertEquals(400, post("/api/query/events", q, token).getStatusCode().value());
    q.remove("tenantId");
    q.put("filters", List.of(Map.of("field", "$where", "op", "eq", "value", "evil")));
    assertEquals(400, post("/api/query/events", q, token).getStatusCode().value());
  }

  @Test
  void immutableSchemasAndKeyRevocation() {
    String token = login("demo-admin@localhost");
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth(token);
    var schema = Map.of("$schema", SchemaRegistry.DIALECT, "type", "object");
    assertEquals(
        400,
        http.exchange(
                "/api/schemas/ORDER_CREATED/1",
                HttpMethod.PUT,
                new HttpEntity<>(schema, h),
                Map.class)
            .getStatusCode()
            .value());
    var key = post("/api/applications/order-service/keys", Map.of(), token).getBody();
    String raw = (String) key.get("apiKey");
    assertNotNull(
        db.findOne(
            org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("hash")
                    .is(Secrets.hash(raw))),
            Document.class,
            "api_keys"));
    assertEquals(
        200,
        http.exchange(
                "/api/applications/order-service/keys/" + key.get("keyId"),
                HttpMethod.DELETE,
                new HttpEntity<>(h),
                String.class)
            .getStatusCode()
            .value());
    assertTrue(
        db.findOne(
                org.springframework.data.mongodb.core.query.Query.query(
                    org.springframework.data.mongodb.core.query.Criteria.where("keyId")
                        .is(key.get("keyId"))),
                Document.class,
                "api_keys")
            .getBoolean("revoked"));
  }
}

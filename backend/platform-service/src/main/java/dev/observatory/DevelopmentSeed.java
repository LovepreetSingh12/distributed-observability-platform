package dev.observatory;

import java.time.Duration;
import java.util.*;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DevelopmentSeed implements ApplicationRunner {
  private final MongoTemplate db;
  private final BCryptPasswordEncoder passwords;
  private final boolean enabled;
  private final String password, key;

  public DevelopmentSeed(
      MongoTemplate db,
      BCryptPasswordEncoder passwords,
      @Value("${demo.seed:false}") boolean enabled,
      @Value("${DEMO_PASSWORD:}") String password,
      @Value("${DEMO_API_KEY:}") String key) {
    this.db = db;
    this.passwords = passwords;
    this.enabled = enabled;
    this.password = password;
    this.key = key;
  }

  public void run(ApplicationArguments args) {
    index("users", new Index().on("email", Direction.ASC).unique());
    index(
        "memberships",
        new Index().on("tenantId", Direction.ASC).on("userId", Direction.ASC).unique());
    index(
        "applications",
        new Index().on("tenantId", Direction.ASC).on("applicationId", Direction.ASC).unique());
    index("api_keys", new Index().on("hash", Direction.ASC).unique());
    index("sessions", new Index().on("hash", Direction.ASC).unique());
    index("sessions", new Index().on("expiresAt", Direction.ASC).expire(Duration.ZERO));
    index(
        "event_schemas",
        new Index()
            .on("tenantId", Direction.ASC)
            .on("eventType", Direction.ASC)
            .on("version", Direction.ASC)
            .unique());
    index(
        "dashboards",
        new Index().on("tenantId", Direction.ASC).on("dashboardId", Direction.ASC).unique());
    if (!enabled) return;
    if (password.length() < 16 || key.length() < 32)
      throw new IllegalArgumentException("Provide generated demo secrets");
    for (String tenant : List.of("demo", "other")) {
      upsert(
          "tenants",
          Criteria.where("tenantId").is(tenant),
          new Document("tenantId", tenant).append("name", tenant));
      for (String role : List.of("ADMIN", "VIEWER")) {
        String id = tenant + "-" + role.toLowerCase();
        upsert(
            "users",
            Criteria.where("userId").is(id),
            new Document("userId", id)
                .append("email", id + "@localhost")
                .append("passwordHash", passwords.encode(password)));
        upsert(
            "memberships",
            Criteria.where("userId").is(id).and("tenantId").is(tenant),
            new Document("userId", id).append("tenantId", tenant).append("role", role));
      }
      upsert(
          "applications",
          Criteria.where("tenantId").is(tenant).and("applicationId").is("order-service"),
          new Document("tenantId", tenant)
              .append("applicationId", "order-service")
              .append("name", "Order Service"));
      schema(
          tenant,
          "ORDER_CREATED",
          "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"orderId\",\"amount\",\"currency\"],\"properties\":{\"orderId\":{\"type\":\"string\",\"maxLength\":100,\"x-queryable\":true},\"amount\":{\"type\":\"number\",\"minimum\":0,\"x-queryable\":true},\"currency\":{\"type\":\"string\",\"enum\":[\"INR\",\"USD\",\"EUR\"],\"x-queryable\":true}}}");
      schema(
          tenant,
          "APPLICATION_LOG",
          "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\",\"required\":[\"logger\",\"thread\"],\"additionalProperties\":false,\"properties\":{\"logger\":{\"type\":\"string\",\"x-queryable\":true},\"thread\":{\"type\":\"string\"},\"exception\":{\"type\":\"string\"}}}");
    }
    upsert(
        "api_keys",
        Criteria.where("hash").is(Secrets.hash(key)),
        new Document("hash", Secrets.hash(key))
            .append("keyId", "demo-seed")
            .append("tenantId", "demo")
            .append("applicationId", "order-service")
            .append("revoked", false));
  }

  void index(String collection, Index i) {
    db.indexOps(collection).ensureIndex(i);
  }

  void upsert(String collection, Criteria c, Document d) {
    Update u = new Update();
    d.forEach(u::setOnInsert);
    db.upsert(Query.query(c), u, collection);
  }

  void schema(String tenant, String type, String json) {
    upsert(
        "event_schemas",
        Criteria.where("tenantId").is(tenant).and("eventType").is(type).and("version").is(1),
        new Document("tenantId", tenant)
            .append("eventType", type)
            .append("version", 1)
            .append("published", true)
            .append("schema", json));
  }
}

package dev.observatory;

import java.time.Duration;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class StorageIndexes {
  private final MongoTemplate db;

  public StorageIndexes(MongoTemplate db) {
    this.db = db;
  }

  @jakarta.annotation.PostConstruct
  public void initialize() {
    var i = db.indexOps("events");
    i.ensureIndex(new Index().on("tenantId", Direction.ASC).on("eventId", Direction.ASC).unique());
    i.ensureIndex(
        new Index()
            .on("tenantId", Direction.ASC)
            .on("applicationId", Direction.ASC)
            .on("occurredAt", Direction.DESC)
            .on("eventId", Direction.DESC));
    i.ensureIndex(
        new Index()
            .on("tenantId", Direction.ASC)
            .on("occurredAt", Direction.DESC)
            .on("eventId", Direction.DESC));
    i.ensureIndex(
        new Index()
            .on("tenantId", Direction.ASC)
            .on("eventType", Direction.ASC)
            .on("occurredAt", Direction.DESC));
    i.ensureIndex(
        new Index()
            .on("tenantId", Direction.ASC)
            .on("traceId", Direction.ASC)
            .on("occurredAt", Direction.DESC));
    i.ensureIndex(new Index().on("expiresAt", Direction.ASC).expire(Duration.ZERO));
  }
}

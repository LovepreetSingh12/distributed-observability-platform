package dev.observatory;

import com.mongodb.*;
import com.mongodb.client.model.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class EventStorage {
  private final MongoTemplate db;
  private final KafkaTemplate<String, String> kafka;

  public EventStorage(MongoTemplate db, KafkaTemplate<String, String> kafka) {
    this.db = db;
    this.kafka = kafka;
  }

  @KafkaListener(topics = "events.ingested.v1")
  public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
    List<WriteModel<Document>> writes = new ArrayList<>();
    List<ConsumerRecord<String, String>> valid = new ArrayList<>();
    for (var r : records) {
      try {
        Document d = Document.parse(r.value());
        for (String field : List.of("tenantId", "eventId", "applicationId", "eventType"))
          if (d.getString(field) == null || d.getString(field).isBlank())
            throw new IllegalArgumentException("Missing identity");
        UUID.fromString(d.getString("eventId"));
        for (String field : List.of("occurredAt", "receivedAt", "expiresAt"))
          d.put(field, Date.from(Instant.parse(d.getString(field))));
        writes.add(new InsertOneModel<>(d));
        valid.add(r);
      } catch (Exception e) {
        dlq(r, "Malformed record");
      }
    }
    if (!writes.isEmpty()) {
      try {
        db.getCollection("events")
            .withWriteConcern(WriteConcern.MAJORITY.withJournal(true))
            .bulkWrite(writes, new BulkWriteOptions().ordered(false));
      } catch (MongoBulkWriteException e) {
        if (e.getWriteConcernError() != null) throw e;
        for (var error : e.getWriteErrors()) {
          if (error.getCode() == 11000) continue;
          if (error.getCode() == 121 || error.getCode() == 2)
            dlq(valid.get(error.getIndex()), "Permanent storage failure code " + error.getCode());
          else throw e;
        }
      }
    }
    ack.acknowledge();
  }

  void dlq(ConsumerRecord<String, String> r, String reason) {
    Document d =
        new Document("original", r.value())
            .append("key", r.key())
            .append("sourceTopic", r.topic())
            .append("partition", r.partition())
            .append("offset", r.offset())
            .append("failedAt", Instant.now().toString())
            .append("reason", reason);
    try {
      kafka.send("events.dlq.v1", r.key(), d.toJson()).get(12, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException("DLQ publication failed; offsets must not commit", e);
    }
  }
}

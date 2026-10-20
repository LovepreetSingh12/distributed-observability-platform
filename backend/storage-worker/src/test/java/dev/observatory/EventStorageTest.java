package dev.observatory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class EventStorageTest {
  @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0.15");
  MongoTemplate db;
  KafkaTemplate<String, String> kafka;
  EventStorage storage;

  @BeforeEach
  void setup() {
    db =
        new MongoTemplate(com.mongodb.client.MongoClients.create(mongo.getReplicaSetUrl()), "test");
    db.dropCollection("events");
    new StorageIndexes(db).initialize();
    kafka = mock(KafkaTemplate.class);
    storage = new EventStorage(db, kafka);
  }

  ConsumerRecord<String, String> record(String id) {
    return new ConsumerRecord<>(
        "events.ingested.v1",
        0,
        0,
        "tenant:app:" + id,
        new Document("tenantId", "tenant")
            .append("eventId", id)
            .append("applicationId", "app")
            .append("eventType", "ORDER_CREATED")
            .append("occurredAt", Instant.now().toString())
            .append("receivedAt", Instant.now().toString())
            .append("expiresAt", Instant.now().plusSeconds(3600).toString())
            .toJson());
  }

  @Test
  void duplicateAndPartialBulkSuccessAreSafe() {
    String id = UUID.randomUUID().toString();
    Acknowledgment ack = mock(Acknowledgment.class);
    storage.consume(List.of(record(id)), ack);
    storage.consume(List.of(record(id), record(UUID.randomUUID().toString())), ack);
    assertEquals(2, db.getCollection("events").countDocuments());
    verify(ack, times(2)).acknowledge();
    verifyNoInteractions(kafka);
  }

  @Test
  void databaseFailureDoesNotCommit() {
    MongoTemplate unavailable = mock(MongoTemplate.class);
    when(unavailable.getCollection("events"))
        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
    Acknowledgment ack = mock(Acknowledgment.class);
    assertThrows(
        Exception.class,
        () ->
            new EventStorage(unavailable, kafka)
                .consume(List.of(record(UUID.randomUUID().toString())), ack));
    verifyNoInteractions(ack);
  }

  @Test
  void dlqFailureDoesNotCommit() {
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("offline")));
    Acknowledgment ack = mock(Acknowledgment.class);
    assertThrows(
        IllegalStateException.class,
        () ->
            storage.consume(
                List.of(new ConsumerRecord<>("events.ingested.v1", 0, 0, "key", "invalid")), ack));
    verifyNoInteractions(ack);
  }

  @Test
  void malformedRecordCommitsOnlyAfterDlqAcknowledgment() {
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    Acknowledgment ack = mock(Acknowledgment.class);
    storage.consume(
        List.of(new ConsumerRecord<>("events.ingested.v1", 0, 0, "key", "invalid")), ack);
    var order = inOrder(kafka, ack);
    order.verify(kafka).send(eq("events.dlq.v1"), eq("key"), contains("original"));
    order.verify(ack).acknowledge();
  }
}

package dev.observatory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;

class IngestionTest {
  @Test
  void partialOutcomesAndIdentity() throws Exception {
    MongoTemplate db = mock(MongoTemplate.class);
    SchemaRegistry schemas = mock(SchemaRegistry.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    ObjectMapper m =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    when(db.findOne(any(Query.class), eq(Document.class), eq("api_keys")))
        .thenReturn(new Document("tenantId", "tenant").append("applicationId", "app"));
    when(schemas.validate(any(), any())).thenReturn(List.of());
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    var controller = new IngestionController(db, m, schemas, kafka, 100, 100, 14);
    var batch = m.createArrayNode();
    batch.add(
        m.valueToTree(
            new Event(
                UUID.randomUUID().toString(),
                1,
                "ORDER_CREATED",
                1,
                Instant.now(),
                "app",
                "dev",
                "INFO",
                "ok",
                null,
                null,
                m.createObjectNode())));
    batch.add(
        m.valueToTree(
            new Event(
                UUID.randomUUID().toString(),
                1,
                "ORDER_CREATED",
                1,
                Instant.now(),
                "other",
                "dev",
                "INFO",
                "bad",
                null,
                null,
                m.createObjectNode())));
    var results = (List<BatchResult>) controller.ingest("Bearer secret", batch).getBody();
    assertEquals("accepted", results.get(0).status());
    assertEquals("rejected", results.get(1).status());
    verify(kafka, times(1))
        .send(eq("events.ingested.v1"), startsWith("tenant:app:"), contains("expiresAt"));
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
    results = (List<BatchResult>) controller.ingest("Bearer secret", batch).getBody();
    assertEquals("retryable", results.get(0).status());
    when(db.findOne(any(Query.class), eq(Document.class), eq("api_keys"))).thenReturn(null);
    assertEquals(401, controller.ingest("Bearer revoked", batch).getStatusCode().value());
  }
}

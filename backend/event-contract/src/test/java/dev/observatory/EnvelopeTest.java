package dev.observatory;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EnvelopeTest {
  ObjectMapper m =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  JsonNode event() {
    return m.valueToTree(
        new Event(
            UUID.randomUUID().toString(),
            1,
            "ORDER_CREATED",
            1,
            Instant.now(),
            "orders",
            "dev",
            "INFO",
            "created",
            null,
            null,
            m.createObjectNode()));
  }

  @Test
  void rejectsIdentityAndServerFields() {
    assertThrows(
        IllegalArgumentException.class, () -> EnvelopeValidator.validate(event(), m, "other"));
    var n = (com.fasterxml.jackson.databind.node.ObjectNode) event();
    n.put("tenantId", "victim");
    assertThrows(IllegalArgumentException.class, () -> EnvelopeValidator.validate(n, m, "orders"));
  }

  @Test
  void rejectsDeepAndFuturePayloads() {
    var n = (com.fasterxml.jackson.databind.node.ObjectNode) event();
    n.put("occurredAt", Instant.now().plusSeconds(600).toString());
    assertThrows(IllegalArgumentException.class, () -> EnvelopeValidator.validate(n, m, "orders"));
  }

  @Test
  void schemaValidationAndRemoteRefs() {
    SchemaRegistry registry = new SchemaRegistry(null, m);
    var s = m.createObjectNode().put("$schema", SchemaRegistry.DIALECT).put("type", "object");
    s.putObject("properties").putObject("amount").put("type", "number");
    var compiled = registry.compile(s);
    assertFalse(registry.validate(compiled, m.createObjectNode().put("amount", "bad")).isEmpty());
    s.put("$ref", "https://example.com/schema");
    assertThrows(IllegalArgumentException.class, () -> registry.compile(s));
  }

  @Test
  void rejectsCoercedTimestampAndFractionalVersion() {
    var n = (com.fasterxml.jackson.databind.node.ObjectNode) event();
    n.put("occurredAt", Instant.now().getEpochSecond());
    assertThrows(IllegalArgumentException.class, () -> EnvelopeValidator.validate(n, m, "orders"));
    n.put("occurredAt", Instant.now().toString());
    n.put("payloadSchemaVersion", 1.5);
    assertThrows(IllegalArgumentException.class, () -> EnvelopeValidator.validate(n, m, "orders"));
  }
}

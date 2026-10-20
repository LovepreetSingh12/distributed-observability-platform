package dev.observatory;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class EventLoggerTest {
  @Test
  void retriesPreserveIdentityAndHonorPartialOutcomes() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    List<List<String>> requests = new CopyOnWriteArrayList<>();
    CountDownLatch accepted = new CountDownLatch(1);
    ObjectMapper m = new ObjectMapper();
    server.createContext(
        "/batch",
        x -> {
          JsonNode n = m.readTree(x.getRequestBody());
          List<String> ids = new ArrayList<>();
          n.forEach(e -> ids.add(e.path("eventId").asText()));
          requests.add(ids);
          String response;
          if (requests.size() == 1)
            response =
                "[{\"eventId\":\""
                    + ids.get(0)
                    + "\",\"status\":\"rejected\"},{\"eventId\":\""
                    + ids.get(1)
                    + "\",\"status\":\"retryable\"}]";
          else {
            response = "[{\"eventId\":\"" + ids.get(0) + "\",\"status\":\"accepted\"}]";
            accepted.countDown();
          }
          byte[] bytes = response.getBytes();
          x.sendResponseHeaders(200, bytes.length);
          x.getResponseBody().write(bytes);
          x.close();
        });
    server.start();
    try (EventLogger logger =
        new EventLogger(
            new EventLogger.Config(
                URI.create("http://localhost:" + server.getAddress().getPort() + "/batch"),
                "secret",
                "orders",
                "dev",
                10,
                2,
                10000,
                Duration.ofMillis(100),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                false))) {
      logger.info("ORDER_CREATED", 1, "one", Map.of());
      logger.info("ORDER_CREATED", 1, "two", Map.of());
      assertTrue(accepted.await(5, TimeUnit.SECONDS));
      assertEquals(2, requests.size());
      assertEquals(List.of(requests.get(0).get(1)), requests.get(1));
      assertEquals(1, logger.counters().get("rejected"));
      assertEquals(1, logger.counters().get("retried"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void parsesRetryAfter() {
    assertEquals(2000, EventLogger.retryAfter("2"));
    assertEquals(0, EventLogger.retryAfter("invalid"));
  }
}

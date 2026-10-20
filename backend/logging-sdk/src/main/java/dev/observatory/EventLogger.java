package dev.observatory;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.slf4j.MDC;

public class EventLogger implements AutoCloseable {
  	public record Config(
      URI endpoint,
      String apiKey,
      String applicationId,
      String environment,
      int capacity,
      int batchCount,
      int batchBytes,
      Duration flushInterval,
      Duration retryLifetime,
      Duration shutdownTimeout,
      boolean dropOldest) {
    public Config {
      if (capacity < 1
          || batchCount < 1
          || batchBytes < 1024
          || flushInterval.isNegative()
          || flushInterval.isZero())
        throw new IllegalArgumentException("Invalid SDK queue settings");
    }
  }

  record Pending(Event event, long created, int attempts, long due) {}

  private final Config config;
  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final BlockingDeque<Pending> queue;
  private final ScheduledExecutorService executor;
  private final HttpClient http;
  private volatile boolean closing;
  private final AtomicBoolean scheduled = new AtomicBoolean();
  private final AtomicInteger estimatedBytes = new AtomicInteger();
  private final AtomicInteger inflight = new AtomicInteger();
  private final LongAdder queued = new LongAdder(),
      accepted = new LongAdder(),
      retried = new LongAdder(),
      rejected = new LongAdder(),
      dropped = new LongAdder();

  public EventLogger(Config config) {
    this.config = config;
    queue = new LinkedBlockingDeque<>(config.capacity());
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    executor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "observatory-sdk-sender");
              t.setDaemon(true);
              return t;
            });
    executor.scheduleWithFixedDelay(
        this::drain,
        config.flushInterval().toMillis(),
        config.flushInterval().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  public void info(String type, int version, String message, Map<String, ?> payload) {
    emit("INFO", type, version, message, payload);
  }

  public void emit(String level, String type, int version, String message, Map<String, ?> payload) {
    if (closing) {
      dropped.increment();
      return;
    }
    Event e =
        new Event(
            UUID.randomUUID().toString(),
            1,
            type,
            version,
            Instant.now(),
            config.applicationId(),
            config.environment(),
            level,
            message,
            MDC.get("traceId"),
            MDC.get("spanId"),
            mapper.valueToTree(payload));
    Pending p = new Pending(e, System.currentTimeMillis(), 0, 0);
    if (!queue.offer(p)) {
      if (config.dropOldest()) {
        queue.poll();
        dropped.increment();
        if (!queue.offer(p)) dropped.increment();
        else queued.increment();
      } else dropped.increment();
    } else queued.increment();
    if ((queue.size() >= config.batchCount()
            || estimatedBytes.addAndGet(e.payload().toString().length() + 512)
                >= config.batchBytes())
        && !closing
        && scheduled.compareAndSet(false, true)) {
      try {
        executor.execute(
            () -> {
              try {
                drain();
              } finally {
                scheduled.set(false);
              }
            });
      } catch (RejectedExecutionException ignored) {
        scheduled.set(false);
      }
    }
  }

  private void drain() {
    estimatedBytes.set(0);
    List<Pending> batch = new ArrayList<>();
    int bytes = 2;
    int scan = queue.size();
    try {
      while (scan-- > 0 && batch.size() < config.batchCount()) {
        Pending p = queue.poll();
        if (p == null) break;
        if (System.currentTimeMillis() - p.created() > config.retryLifetime().toMillis()) {
          dropped.increment();
          continue;
        }
        if (p.due() > System.currentTimeMillis()) {
          if (!queue.offer(p)) dropped.increment();
          continue;
        }
        int size = mapper.writeValueAsBytes(p.event()).length + 1;
        if (size > config.batchBytes()) {
          rejected.increment();
          continue;
        }
        if (bytes + size > config.batchBytes()) {
          if (!queue.offerFirst(p)) dropped.increment();
          break;
        }
        bytes += size;
        batch.add(p);
      }
      if (batch.isEmpty()) return;
      inflight.set(batch.size());
      String body = mapper.writeValueAsString(batch.stream().map(Pending::event).toList());
      HttpRequest request =
          HttpRequest.newBuilder(config.endpoint())
              .header("Authorization", "Bearer " + config.apiKey())
              .header("Content-Type", "application/json")
              .timeout(Duration.ofSeconds(5))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      long retryAfter = retryAfter(response.headers().firstValue("Retry-After").orElse("0"));
      if (response.statusCode() == 200) {
        JsonNode outcomes = mapper.readTree(response.body());
        if (!outcomes.isArray()) throw new IllegalArgumentException("Invalid batch response");
        Map<String, String> statuses = new HashMap<>();
        outcomes.forEach(n -> statuses.put(n.path("eventId").asText(), n.path("status").asText()));
        for (Pending p : batch) {
          switch (statuses.getOrDefault(p.event().eventId(), "retryable")) {
            case "accepted" -> accepted.increment();
            case "rejected" -> rejected.increment();
            default -> retry(p, retryAfter);
          }
        }
      } else if (response.statusCode() == 429
          || response.statusCode() >= 500
          || response.statusCode() == 408) {
        batch.forEach(p -> retry(p, retryAfter));
      } else batch.forEach(p -> rejected.increment());
    } catch (Exception e) {
      batch.forEach(p -> retry(p, 0));
    } finally {
      inflight.set(0);
    }
  }

  static long retryAfter(String value) {
    try {
      return Math.max(0, Long.parseLong(value) * 1000);
    } catch (Exception e) {
      try {
        return Math.max(
            0,
            Duration.between(
                    Instant.now(),
                    ZonedDateTime.parse(
                            value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant())
                .toMillis());
      } catch (Exception ignored) {
        return 0;
      }
    }
  }

  private void retry(Pending p, long retryAfter) {
    long now = System.currentTimeMillis();
    long delay =
        Math.max(
            retryAfter,
            Math.min(30000, 250L << Math.min(7, p.attempts()))
                + ThreadLocalRandom.current().nextLong(250));
    if (now + delay - p.created() > config.retryLifetime().toMillis() || closing) {
      dropped.increment();
      return;
    }
    if (queue.offer(new Pending(p.event(), p.created(), p.attempts() + 1, now + delay)))
      retried.increment();
    else dropped.increment();
  }

  public Map<String, Long> counters() {
    return Map.of(
        "queued",
        queued.sum(),
        "accepted",
        accepted.sum(),
        "retried",
        retried.sum(),
        "rejected",
        rejected.sum(),
        "dropped",
        dropped.sum(),
        "pending",
        (long) queue.size() + inflight.get());
  }

  public void close() {
    closing = true;
    long end = System.nanoTime() + config.shutdownTimeout().toNanos();
    try {
      executor.execute(
          () -> {
            while (!queue.isEmpty() && System.nanoTime() < end) {
              Pending p = queue.poll();
              if (p != null) queue.offerFirst(new Pending(p.event(), p.created(), p.attempts(), 0));
              drain();
            }
          });
    } catch (RejectedExecutionException ignored) {
    }
    executor.shutdown();
    try {
      if (!executor.awaitTermination(config.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS))
        executor.shutdownNow();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
    }
    dropped.add(queue.size());
    queue.clear();
  }
}

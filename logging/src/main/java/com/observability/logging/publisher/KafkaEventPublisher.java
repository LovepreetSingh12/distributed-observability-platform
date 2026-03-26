package com.observability.logging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observability.logging.model.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;
    private final String                        topic;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic         = topic;
        this.objectMapper  = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Publishes a LogEvent to Kafka asynchronously.
     *
     * Partition key = traceId so all events for the same trace land
     * on the same partition, preserving ordering within a trace.
     */
    public void publish(LogEvent event) {
        try {
            String payload      = objectMapper.writeValueAsString(event);
            String partitionKey = event.getTraceId() != null
                                  ? event.getTraceId()
                                  : event.getEventId();

            CompletableFuture<?> future = kafkaTemplate.send(topic, partitionKey, payload);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    // WARN not ERROR — a dropped event is bad but not fatal
                    log.warn("[SDK] Failed to publish event={} to topic={}: {}",
                             event.getEventId(), topic, ex.getMessage());
                }
            });

        } catch (Exception e) {
            // Serialisation error — log and swallow, never propagate
            log.warn("[SDK] Serialisation error for event={}: {}",
                     event.getEventId(), e.getMessage());
        }
    }
}
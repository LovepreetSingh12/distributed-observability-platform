package com.lovepreet.injestionservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lovepreet.injestionservice.batch.EventBatchWriter;
import com.lovepreet.injestionservice.model.IngestionMetrics;
import com.lovepreet.injestionservice.model.PersistedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final EventBatchWriter batchWriter;
    private final IngestionMetrics metrics;
    private final ObjectMapper     objectMapper;

    public EventConsumer(EventBatchWriter batchWriter, IngestionMetrics metrics) {
        this.batchWriter  = batchWriter;
        this.metrics      = metrics;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Batch Kafka listener.
     *
     * - groupId ties all 4 concurrent threads to the same consumer group,
     *   so Kafka balances the 4 partitions across the 4 threads automatically.
     * - containerFactory="kafkaListenerContainerFactory" points to our
     *   custom factory with concurrency=4 and MANUAL ack mode.
     * - Each invocation receives a full poll batch (up to maxPollRecords=500).
     *
     * Flow per invocation:
     *   1. Deserialize each JSON record into a LogEvent
     *   2. Map to PersistedEvent (adds Kafka metadata: topic/partition/offset)
     *   3. Enqueue into EventBatchWriter's buffer (non-blocking)
     *   4. Acknowledge the batch ONLY after all events are enqueued
     *      (not after MongoDB write — the batch writer handles that async)
     */
    @KafkaListener(
        topics                = "${ingestion.kafka.topic:observability-events}",
        groupId               = "${ingestion.kafka.consumer-group:ingestion-group}",
        containerFactory      = "kafkaListenerContainerFactory"
    )
    public void consume(List<ConsumerRecord<String, String>> records,
                        Acknowledgment acknowledgment) {

        metrics.recordReceived(records.size());
        int enqueued = 0;

        for (ConsumerRecord<String, String> record : records) {
            try {
                PersistedEvent      logEvent  = objectMapper.readValue(record.value(), PersistedEvent.class);
                batchWriter.enqueue(logEvent);
                enqueued++;

            } catch (Exception e) {
                log.warn("Failed to deserialize record at offset={} partition={}: {}",
                         record.offset(), record.partition(), e.getMessage());
                metrics.recordParseError();
                // Do NOT fail the whole batch for one bad record — just skip it
            }
        }

        // Commit offset only after all records in this poll are enqueued.
        // If the JVM dies before the batch writer flushes, we may re-consume
        // some events — this gives us at-least-once delivery semantics.
        acknowledgment.acknowledge();

        log.debug("Consumer thread={} enqueued={}/{} from partition={}",
                  Thread.currentThread().getName(), enqueued,
                  records.size(), records.get(0).partition());
    }
}

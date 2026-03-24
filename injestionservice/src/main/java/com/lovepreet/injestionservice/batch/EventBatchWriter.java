package com.lovepreet.injestionservice.batch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.lovepreet.injestionservice.model.IngestionMetrics;
import com.lovepreet.injestionservice.model.PersistedEvent;

@Component
public class EventBatchWriter {
    private static final Logger log = Logger.getLogger(EventBatchWriter.class.getName());

    private static final int  QUEUE_CAPACITY  = 50_000;
    private static final int  MAX_BATCH_SIZE  = 1_000;   // max docs per MongoDB bulk insert
    private static final long FLUSH_INTERVAL  = 2_000L;  // ms between forced flushes

    // BlockingQueue decouples the Kafka consumer threads from the MongoDB write thread.
    // Multiple consumer threads offer() to it; the scheduled flush drains it.
    private final BlockingQueue<PersistedEvent> buffer =
            new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    private final MongoTemplate    mongoTemplate;
    private final IngestionMetrics metrics;

    public EventBatchWriter(MongoTemplate mongoTemplate, IngestionMetrics metrics) {
        this.mongoTemplate = mongoTemplate;
        this.metrics       = metrics;
    }

    /**
     * Called by EventConsumer threads to enqueue a single event.
     * Non-blocking: if the queue is full, we drop and record the error
     * (back-pressure signal — means consumers are outpacing writes).
     */
    public void enqueue(PersistedEvent event) {
        if (!buffer.offer(event)) {
            log.warning("BatchWriter buffer full — dropping event from service={}" + event.getServiceName());
            metrics.recordWriteError();
        }
    }

    /**
     * Scheduled flush: drains up to MAX_BATCH_SIZE events from the queue
     * and writes them to MongoDB in a single bulk insert.
     *
     * Runs every FLUSH_INTERVAL ms on a dedicated Spring scheduler thread.
     * This means the MongoDB write never blocks the Kafka consumer threads.
     */
    @Scheduled(fixedDelay = FLUSH_INTERVAL)
    public void flush() {
        if (buffer.isEmpty()) return;

        List<PersistedEvent> batch = new ArrayList<>(MAX_BATCH_SIZE);
        // drainTo is atomic and much faster than polling in a loop
        int drained = buffer.drainTo(batch, MAX_BATCH_SIZE);

        if (drained == 0) return;

        long start = System.currentTimeMillis();
        try {
            writeBatch(batch);
            long ms = System.currentTimeMillis() - start;
            metrics.recordBatch(drained, ms);
            metrics.recordPersisted(drained);
            log.info("Flushed batch of {" + drained + "} events in {" + ms + " ms}");

        } catch (Exception e) {
            log.severe("Batch write failed for {" + drained  + "} events: {"+e.getMessage() + "}");
            metrics.recordWriteError();
            // Re-queue failed events so they are not lost (best-effort)
            batch.forEach(this::enqueue);
        }
    }

    /**
     * Single MongoDB round-trip for the entire batch using BulkOperations.
     * UNORDERED mode: MongoDB processes inserts in parallel internally and
     * does not stop on a single document failure.
     */
    private void writeBatch(List<PersistedEvent> batch) {
        BulkOperations bulk = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED, PersistedEvent.class);

        for (PersistedEvent event : batch) {
            event.setIngestedAt(Instant.now());
            bulk.insert(event);
        }

        bulk.execute();
    }

    public int getBufferSize() {
        return buffer.size();
    }
}

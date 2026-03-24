package com.lovepreet.injestionservice.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class IngestionMetrics {
    private final AtomicLong totalReceived  = new AtomicLong();
    private final AtomicLong totalPersisted = new AtomicLong();
    private final AtomicLong totalBatches   = new AtomicLong();
    private final AtomicLong parseErrors    = new AtomicLong();
    private final AtomicLong writeErrors    = new AtomicLong();
    private final Instant    startedAt      = Instant.now();

    private volatile Instant lastBatchAt;
    private volatile long    lastBatchSize;
    private volatile long    lastBatchMs;

    public void recordReceived(int n)  { totalReceived.addAndGet(n); }
    public void recordPersisted(int n) { totalPersisted.addAndGet(n); }
    public void recordParseError()     { parseErrors.incrementAndGet(); }
    public void recordWriteError()     { writeErrors.incrementAndGet(); }

    public void recordBatch(int size, long ms) {
        totalBatches.incrementAndGet();
        lastBatchAt   = Instant.now();
        lastBatchSize = size;
        lastBatchMs   = ms;
    }

    public long getReceived()       { return totalReceived.get(); }
    public long getPersisted()      { return totalPersisted.get(); }
    public long getBatches()        { return totalBatches.get(); }
    public long getParseErrors()    { return parseErrors.get(); }
    public long getWriteErrors()    { return writeErrors.get(); }
    public Instant getLastBatchAt() { return lastBatchAt; }
    public long getLastBatchSize()  { return lastBatchSize; }
    public long getLastBatchMs()    { return lastBatchMs; }
    public Instant getStartedAt()   { return startedAt; }

    public double getEventsPerSecond() {
        long secs = java.time.Duration.between(startedAt, Instant.now()).getSeconds();
        return secs == 0 ? 0.0 : (double) totalPersisted.get() / secs;
    }
}

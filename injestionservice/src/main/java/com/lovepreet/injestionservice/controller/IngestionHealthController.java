package com.lovepreet.injestionservice.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lovepreet.injestionservice.batch.EventBatchWriter;
import com.lovepreet.injestionservice.model.IngestionMetrics;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionHealthController {

    private final IngestionMetrics metrics;
    private final EventBatchWriter batchWriter;

    public IngestionHealthController(IngestionMetrics metrics, EventBatchWriter batchWriter) {
        this.metrics     = metrics;
        this.batchWriter = batchWriter;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("startedAt",       metrics.getStartedAt());
        m.put("totalReceived",   metrics.getReceived());
        m.put("totalPersisted",  metrics.getPersisted());
        m.put("totalBatches",    metrics.getBatches());
        m.put("parseErrors",     metrics.getParseErrors());
        m.put("writeErrors",     metrics.getWriteErrors());
        m.put("eventsPerSecond", Math.round(metrics.getEventsPerSecond() * 100.0) / 100.0);
        m.put("lastBatchAt",     metrics.getLastBatchAt());
        m.put("lastBatchSize",   metrics.getLastBatchSize());
        m.put("lastBatchMs",     metrics.getLastBatchMs());
        m.put("bufferDepth",     batchWriter.getBufferSize());
        return m;
    }
}

package com.lovepreet.injestionservice;

import com.lovepreet.injestionservice.batch.EventBatchWriter;
import com.lovepreet.injestionservice.consumer.EventConsumer;
import com.lovepreet.injestionservice.model.IngestionMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock EventBatchWriter  batchWriter;
    @Mock Acknowledgment    ack;

    @Test
    void validRecord_isEnqueuedAndAcknowledged() {
        IngestionMetrics metrics = new IngestionMetrics();
        EventConsumer    consumer = new EventConsumer(batchWriter, metrics);

        String json = """
            {
              "eventId":"e1","traceId":"t1","serviceName":"svc",
              "level":"INFO","eventType":"LOG","message":"hello",
              "timestamp":"2024-01-01T00:00:00Z"
            }
            """;

        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("observability-events", 0, 0L, "t1", json);

        consumer.consume(List.of(record), ack);

        verify(batchWriter, times(1)).enqueue(any());
        verify(ack,         times(1)).acknowledge();
        assert metrics.getReceived() == 1;
    }

    @Test
    void malformedRecord_isSkippedWithoutFailingBatch() {
        IngestionMetrics metrics  = new IngestionMetrics();
        EventConsumer    consumer = new EventConsumer(batchWriter, metrics);

        ConsumerRecord<String, String> bad =
            new ConsumerRecord<>("observability-events", 0, 1L, "k", "NOT_JSON{{{");

        consumer.consume(List.of(bad), ack);

        // Batch still acks — bad record is counted as parse error, not thrown
        verify(batchWriter, never()).enqueue(any());
        verify(ack,         times(1)).acknowledge();
        assert metrics.getParseErrors() == 1;
    }
}
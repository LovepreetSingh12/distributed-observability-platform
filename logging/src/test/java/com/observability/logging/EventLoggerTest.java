package com.observability.logging;

import com.observability.logging.model.TraceContext;
import com.observability.logging.publisher.EventLogger;
import com.observability.logging.publisher.KafkaEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventLoggerTest {

    @Mock KafkaTemplate<String, String> kafkaTemplate;

    KafkaEventPublisher publisher;
    EventLogger         eventLogger;

    @BeforeEach
    void setUp() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        publisher   = new KafkaEventPublisher(kafkaTemplate, "test-events");
        eventLogger = new EventLogger(publisher, "test-service", "1.0.0");

        TraceContext.setTraceId("trace-abc");
        TraceContext.setSpanId("span-123");
    }

    @Test
    void info_publishesOneEventToKafka() {
        eventLogger.info("Hello");
        verify(kafkaTemplate, times(1))
            .send(eq("test-events"), anyString(), anyString());
    }

    @Test
    void error_withException_includesStackTraceInPayload() {
        RuntimeException ex = new RuntimeException("boom");
        eventLogger.error("Something broke", ex);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("test-events"), anyString(), captor.capture());

        String payload = captor.getValue();
        assertThat(payload).contains("EXCEPTION");
        assertThat(payload).contains("boom");
        assertThat(payload).contains("RuntimeException");
    }

    @Test
    void request_setsHttpFieldsInPayload() {
        eventLogger.request("POST", "/api/orders", 201, 45L);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("test-events"), anyString(), captor.capture());

        String payload = captor.getValue();
        assertThat(payload).contains("REQUEST");
        assertThat(payload).contains("POST");
        assertThat(payload).contains("/api/orders");
    }

    @Test
    void traceId_fromMDC_isStampedOnEveryEvent() {
        eventLogger.warn("Watch out");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        assertThat(captor.getValue()).contains("trace-abc");
    }

    @Test
    void serviceName_isStampedOnEveryEvent() {
        eventLogger.info("Ping");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());

        assertThat(captor.getValue()).contains("test-service");
    }

    @Test
    void traceContext_generatesIds_whenMDCIsEmpty() {
        TraceContext.clear();

        String traceId = TraceContext.getTraceId();
        String spanId  = TraceContext.getSpanId();

        assertThat(traceId).isNotNull().hasSize(16);
        assertThat(spanId).isNotNull().hasSize(8);
    }

    // @Test
    // void kafkaFailure_doesNotThrow() {
    //     when(kafkaTemplate.send(anyString(), anyString(), anyString()))
    //         .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down")));

    //     // Must not throw — SDK failures are silent
    //     assertThat(() -> eventLogger.info("Hello")).doesNotThrowAnyException();
    // }
}
package com.lovepreet.injestionservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${ingestion.kafka.consumer-group:ingestion-group}")
    private String consumerGroup;

    @Value("${ingestion.kafka.concurrency:4}")
    private int concurrency;           // 4 parallel consumer threads per topic partition

    @Value("${ingestion.kafka.max-poll-records:500}")
    private int maxPollRecords;        // 500 records per poll — feeds the batch writer

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,       bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,                consumerGroup);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,  StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,        maxPollRecords);

        // Disable auto-commit — we manually ack after a successful batch write
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,      false);

        // How long to wait before the broker considers this consumer dead
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,      30_000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,   10_000);

        // Fetch at least 1MB per poll (batch efficiency), wait up to 500ms to fill it
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG,         1_048_576);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,       500);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // ── KEY: this is how we get parallel consumers ──────────────────────
        // With 4 partitions on the topic and concurrency=4, each thread owns
        // one partition. Scale concurrency = scale throughput linearly.
        factory.setConcurrency(concurrency);

        // BATCH mode: @KafkaListener methods receive List<String> instead of String
        factory.setBatchListener(true);

        // MANUAL_IMMEDIATE: we call Acknowledgment.acknowledge() ourselves
        // after the batch is safely written to MongoDB
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // If a batch fails, seek back to the beginning of the batch and retry
        factory.getContainerProperties().setSyncCommits(true);

        return factory;
    }
}

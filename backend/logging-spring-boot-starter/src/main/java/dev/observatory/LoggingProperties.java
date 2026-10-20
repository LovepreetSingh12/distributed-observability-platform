package dev.observatory;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("observatory.logging")
public record LoggingProperties(
    String endpoint,
    String apiKey,
    String applicationId,
    String environment,
    Integer capacity,
    Integer batchCount,
    Integer batchBytes,
    Duration flushInterval,
    Duration retryLifetime,
    Duration shutdownTimeout,
    Boolean dropOldest,
    Boolean logback) {}

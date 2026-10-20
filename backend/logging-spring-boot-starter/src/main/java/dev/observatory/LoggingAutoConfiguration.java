package dev.observatory;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(LoggingProperties.class)
@ConditionalOnProperty(prefix = "observatory.logging", name = "api-key")
public class LoggingAutoConfiguration {
  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  EventLogger eventLogger(LoggingProperties p) {
    return new EventLogger(
        new EventLogger.Config(
            URI.create(
                p.endpoint() == null ? "http://localhost:8081/api/v1/events/batch" : p.endpoint()),
            p.apiKey(),
            p.applicationId(),
            p.environment() == null ? "development" : p.environment(),
            p.capacity() == null ? 4096 : p.capacity(),
            p.batchCount() == null ? 50 : p.batchCount(),
            p.batchBytes() == null ? 262144 : p.batchBytes(),
            p.flushInterval() == null ? Duration.ofSeconds(1) : p.flushInterval(),
            p.retryLifetime() == null ? Duration.ofMinutes(2) : p.retryLifetime(),
            p.shutdownTimeout() == null ? Duration.ofSeconds(6) : p.shutdownTimeout(),
            Boolean.TRUE.equals(p.dropOldest())));
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "observatory.logging",
      name = "logback",
      havingValue = "true",
      matchIfMissing = true)
  AutoCloseable logbackBridge(EventLogger logger) {
    ch.qos.logback.classic.Logger root =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    EventLogbackAppender appender = new EventLogbackAppender(logger);
    appender.setContext(root.getLoggerContext());
    appender.start();
    root.addAppender(appender);
    return () -> {
      root.detachAppender(appender);
      appender.stop();
    };
  }
}

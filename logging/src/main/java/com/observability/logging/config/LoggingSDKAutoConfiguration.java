package com.observability.logging.config;

import com.observability.logging.aspect.ObservabilityAspect;
import com.observability.logging.filter.RequestLoggingFilter;
import com.observability.logging.filter.TracingFilter;
import com.observability.logging.publisher.EventLogger;
import com.observability.logging.publisher.KafkaEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(LoggingSDKProperties.class)
@ConditionalOnProperty(
    prefix      = "observability.sdk",
    name        = "enabled",
    havingValue = "true",
    matchIfMissing = true  // SDK is ON by default
)
public class LoggingSDKAutoConfiguration {

    private static final Logger log =
        LoggerFactory.getLogger(LoggingSDKAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public KafkaEventPublisher kafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            LoggingSDKProperties props) {

        log.info("[SDK] KafkaEventPublisher initialised → topic: {}", props.getKafkaTopic());
        return new KafkaEventPublisher(kafkaTemplate, props.getKafkaTopic());
    }

    @Bean
    @ConditionalOnMissingBean
    public EventLogger eventLogger(
            KafkaEventPublisher publisher,
            LoggingSDKProperties props,
            @Value("${spring.application.name:unknown-service}") String appName) {

        String name = props.getServiceName() != null ? props.getServiceName() : appName;
        log.info("[SDK] EventLogger ready → service: {}", name);
        return new EventLogger(publisher, name, props.getServiceVersion());
    }

    @Bean
    @ConditionalOnMissingBean
    public TracingFilter tracingFilter() {
        return new TracingFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "observability.sdk", name = "log-requests",
        havingValue = "true", matchIfMissing = true)
    public RequestLoggingFilter requestLoggingFilter(EventLogger eventLogger) {
        return new RequestLoggingFilter(eventLogger);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "observability.sdk", name = "aop-enabled",
        havingValue = "true", matchIfMissing = true)
    public ObservabilityAspect observabilityAspect(EventLogger eventLogger) {
        return new ObservabilityAspect(eventLogger);
    }
}

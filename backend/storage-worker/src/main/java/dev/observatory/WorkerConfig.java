package dev.observatory;

import org.springframework.context.annotation.*;
import org.springframework.kafka.listener.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.backoff.*;

@Configuration
public class WorkerConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity h) throws Exception {
    return h.csrf(c -> c.disable())
        .authorizeHttpRequests(
            a -> a.requestMatchers("/actuator/health").permitAll().anyRequest().denyAll())
        .build();
  }

  @Bean
  DefaultErrorHandler retryHandler() {
    ExponentialBackOff b = new ExponentialBackOff(500, 2);
    b.setMaxInterval(10000);
    return new DefaultErrorHandler(
        (record, error) -> {
          throw new IllegalStateException("Unresolved batch must be replayed", error);
        },
        b);
  }
}

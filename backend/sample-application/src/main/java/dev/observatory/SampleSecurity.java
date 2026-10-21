package dev.observatory;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SampleSecurity {
	@Bean
	SecurityFilterChain security(HttpSecurity h) throws Exception {
		return h.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
	}
}

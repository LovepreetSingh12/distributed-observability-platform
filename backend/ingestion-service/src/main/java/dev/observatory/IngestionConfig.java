package dev.observatory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class IngestionConfig {
	@Bean
	SecurityFilterChain security(HttpSecurity h) throws Exception {
		return h.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
	}
	
	@Bean
	SchemaRegistry schemas(MongoTemplate db, ObjectMapper m) {
		return new SchemaRegistry(db, m);
	}
}

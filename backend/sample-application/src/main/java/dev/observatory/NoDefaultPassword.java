package dev.observatory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NoDefaultPassword {
	@Bean
	org.springframework.security.core.userdetails.UserDetailsService noDefaultUser() {
		return u -> {
			throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
					"No interactive users");
		};
	}
}

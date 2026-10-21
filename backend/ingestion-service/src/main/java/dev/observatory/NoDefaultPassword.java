package dev.observatory;

import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@org.springframework.context.annotation.Configuration
public class NoDefaultPassword {

	@Bean
	UserDetailsService noDefaultUser() {
		return u -> {
			throw new UsernameNotFoundException(
				"No interactive users");
			};
	}
}

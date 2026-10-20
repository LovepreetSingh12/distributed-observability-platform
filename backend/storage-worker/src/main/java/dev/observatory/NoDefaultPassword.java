package dev.observatory;

@org.springframework.context.annotation.Configuration
public class NoDefaultPassword {
  @org.springframework.context.annotation.Bean
  org.springframework.security.core.userdetails.UserDetailsService noDefaultUser() {
    return u -> {
      throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
          "No interactive users");
    };
  }
}

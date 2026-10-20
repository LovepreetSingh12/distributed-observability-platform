package dev.observatory;

import org.springframework.security.core.context.SecurityContextHolder;

public record UserScope(String userId, String tenantId, String role, String email) {
  public static UserScope current() {
    return (UserScope) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  public static String tenant() {
    return current().tenantId();
  }
}

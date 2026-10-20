package dev.observatory;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.bson.Document;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class PlatformSecurity {
  @Bean
  BCryptPasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain security(HttpSecurity h, MongoTemplate db) throws Exception {
    return h.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((req, res, x) -> res.setStatus(401))
                    .accessDeniedHandler((req, res, x) -> res.setStatus(403)))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/auth/login", "/actuator/health")
                    .permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/**")
                    .hasAnyRole("ADMIN", "VIEWER")
                    .requestMatchers("/api/query/**", "/api/auth/logout")
                    .hasAnyRole("ADMIN", "VIEWER")
                    .anyRequest()
                    .hasRole("ADMIN"))
        .addFilterBefore(
            new OncePerRequestFilter() {
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                  Document token =
                      db.findOne(
                          Query.query(
                              Criteria.where("hash")
                                  .is(Secrets.hash(header.substring(7)))
                                  .and("expiresAt")
                                  .gt(new Date())),
                          Document.class,
                          "sessions");
                  if (token != null) {
                    Document member =
                        db.findOne(
                            Query.query(
                                Criteria.where("userId")
                                    .is(token.getString("userId"))
                                    .and("tenantId")
                                    .is(token.getString("tenantId"))),
                            Document.class,
                            "memberships");
                    if (member != null) {
                      UserScope scope =
                          new UserScope(
                              token.getString("userId"),
                              member.getString("tenantId"),
                              member.getString("role"),
                              token.getString("email"));
                      SecurityContextHolder.getContext()
                          .setAuthentication(
                              new UsernamePasswordAuthenticationToken(
                                  scope,
                                  null,
                                  List.of(new SimpleGrantedAuthority("ROLE_" + scope.role()))));
                    }
                  }
                }
                chain.doFilter(req, res);
              }
            },
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  org.springframework.security.core.userdetails.UserDetailsService noDefaultUser() {
    return u -> {
      throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
          "Use platform authentication");
    };
  }
}

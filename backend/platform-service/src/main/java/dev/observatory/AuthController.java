package dev.observatory;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
  private final MongoTemplate db;
  private final BCryptPasswordEncoder passwords;
  private final String dummy;

  public AuthController(MongoTemplate db, BCryptPasswordEncoder p) {
    this.db = db;
    passwords = p;
    dummy = p.encode(Secrets.generate());
  }

  public record Login(String email, String password, String tenant) {}

  @PostMapping("/api/auth/login")
  public ResponseEntity<?> login(@RequestBody Login login) {
    if (login.password() == null || login.password().length() > 72 || login.email() == null)
      return ResponseEntity.status(401).build();
    Document u =
        db.findOne(Query.query(Criteria.where("email").is(login.email())), Document.class, "users");
    boolean valid =
        passwords.matches(login.password(), u == null ? dummy : u.getString("passwordHash"));
    if (!valid || u == null)
      return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    Criteria criteria = Criteria.where("userId").is(u.getString("userId"));
    if (login.tenant() != null && !login.tenant().isBlank())
      criteria.and("tenantId").is(login.tenant());
    Document m = db.findOne(Query.query(criteria), Document.class, "memberships");
    if (m == null) return ResponseEntity.status(401).build();
    String token = Secrets.generate();
    db.insert(
        new Document("hash", Secrets.hash(token))
            .append("userId", u.getString("userId"))
            .append("email", u.getString("email"))
            .append("tenantId", m.getString("tenantId"))
            .append("expiresAt", Date.from(Instant.now().plusSeconds(28800))),
        "sessions");
    return ResponseEntity.ok(
        Map.of(
            "token",
            token,
            "expiresIn",
            28800,
            "role",
            m.getString("role"),
            "email",
            u.getString("email")));
  }

  @GetMapping("/api/auth/me")
  public UserScope me() {
    return UserScope.current();
  }

  @PostMapping("/api/auth/logout")
  public void logout(@RequestHeader("Authorization") String token) {
    db.remove(Query.query(Criteria.where("hash").is(Secrets.hash(token.substring(7)))), "sessions");
  }
}

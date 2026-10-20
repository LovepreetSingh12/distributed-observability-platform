package dev.observatory;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<?> invalid(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", e.getMessage() == null ? "Invalid request" : e.getMessage()));
  }

  @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(Map.of("error", "Already exists; published versions are immutable"));
  }

  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  ResponseEntity<?> malformed() {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "Malformed JSON or unknown request field"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> failure(Exception e) {
    return ResponseEntity.status(503).body(Map.of("error", "Operation unavailable; retry later"));
  }
}

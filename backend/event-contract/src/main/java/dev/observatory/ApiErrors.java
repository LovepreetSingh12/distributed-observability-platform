package dev.observatory;

import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error",
                        e.getMessage() == null
                                ? "Invalid request"
                                : e.getMessage()
                ));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> conflict() {
        return ResponseEntity.status(409)
                .body(Map.of(
                        "error",
                        "Already exists; published versions are immutable"
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error",
                        "Malformed JSON or unknown request field"
                ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> failure(Exception e) {
        return ResponseEntity.status(503)
                .body(Map.of(
                        "error",
                        "Operation unavailable; retry later"
                ));
    }
}
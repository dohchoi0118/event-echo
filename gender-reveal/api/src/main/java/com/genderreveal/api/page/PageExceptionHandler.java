package com.genderreveal.api.page;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PageExceptionHandler {

    @ExceptionHandler(SlugAlreadyTakenException.class)
    public ResponseEntity<Map<String, String>> handleSlugTaken(SlugAlreadyTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PageNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(PageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidRevealAtException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRevealAt(InvalidRevealAtException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SlugAllocationExhaustedException.class)
    public ResponseEntity<Map<String, String>> handleSlugAllocationExhausted(SlugAllocationExhaustedException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PageNotOpenException.class)
    public ResponseEntity<Map<String, String>> handlePageNotOpen(PageNotOpenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}

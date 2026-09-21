package com.genderreveal.api.page;

import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Two concurrent requests can both pass the existsBySlug check in
     * PageService.resolveSlug before either insert commits (TOCTOU), so the losing
     * insert surfaces as a UNIQUE constraint violation on pages.slug rather than the
     * SlugAlreadyTakenException thrown by the upfront check. Treat it the same way.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleSlugConflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Slug already taken"));
    }

    /**
     * Thrown by UniqueSlugAllocator.allocate() when it exhausts its retry budget.
     * Genuine server-side exhaustion, not a client error, but still worth a JSON body
     * consistent with the rest of this API instead of Spring's default error page.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleSlugAllocationExhausted(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
    }
}

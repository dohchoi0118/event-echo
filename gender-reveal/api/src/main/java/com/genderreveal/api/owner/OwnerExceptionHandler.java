package com.genderreveal.api.owner;

import com.genderreveal.api.guestbook.GuestbookEntryNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class OwnerExceptionHandler {

    @ExceptionHandler(GuestbookEntryNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntryNotFound(GuestbookEntryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ExtensionAlreadyUsedException.class)
    public ResponseEntity<Map<String, String>> handleExtensionUsed(ExtensionAlreadyUsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}

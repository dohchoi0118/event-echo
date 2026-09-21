package com.genderreveal.api.guess;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GuessExceptionHandler {

    @ExceptionHandler(DuplicateGuessException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateGuess(DuplicateGuessException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}

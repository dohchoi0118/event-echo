package com.genderreveal.api.guess;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GuessExceptionHandler {

    @ExceptionHandler(DuplicateGuessException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateGuess(DuplicateGuessException ex) {
        Map<String, String> body = new HashMap<>();
        body.put("error", ex.getMessage());
        body.put("guessedGender", ex.getExistingGuessedGender());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}

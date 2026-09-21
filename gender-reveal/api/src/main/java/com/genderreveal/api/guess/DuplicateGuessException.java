package com.genderreveal.api.guess;

public class DuplicateGuessException extends RuntimeException {
    public DuplicateGuessException(String slug) {
        super("Guest has already guessed on page: " + slug);
    }
}

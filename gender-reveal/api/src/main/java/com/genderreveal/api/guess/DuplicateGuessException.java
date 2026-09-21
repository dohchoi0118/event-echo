package com.genderreveal.api.guess;

public class DuplicateGuessException extends RuntimeException {

    private final String existingGuessedGender;

    public DuplicateGuessException(String slug, String existingGuessedGender) {
        super("Guest has already guessed on page: " + slug);
        this.existingGuessedGender = existingGuessedGender;
    }

    public String getExistingGuessedGender() {
        return existingGuessedGender;
    }
}

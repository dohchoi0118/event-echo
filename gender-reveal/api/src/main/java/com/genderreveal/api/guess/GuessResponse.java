package com.genderreveal.api.guess;

import java.time.Instant;

public record GuessResponse(String guessedGender, Instant createdAt) {
    static GuessResponse from(Guess guess) {
        return new GuessResponse(guess.getGuessedGender(), guess.getCreatedAt());
    }
}

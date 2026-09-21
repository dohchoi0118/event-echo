package com.genderreveal.api.guess;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record GuessCreateRequest(
    @NotNull @Pattern(regexp = "boy|girl") String guessedGender
) {}

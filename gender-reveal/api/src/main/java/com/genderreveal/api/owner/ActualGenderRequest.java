package com.genderreveal.api.owner;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ActualGenderRequest(
    @NotNull @Pattern(regexp = "boy|girl") String actualGender
) {}

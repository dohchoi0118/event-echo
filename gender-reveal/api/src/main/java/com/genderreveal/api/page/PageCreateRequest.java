package com.genderreveal.api.page;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.LocalDate;

public record PageCreateRequest(
    @NotBlank String nickname,
    @NotNull @Pattern(regexp = "boy|girl") String actualGender,
    @NotNull Instant revealAt,
    LocalDate dueDate,
    String message,
    @NotNull @Pattern(regexp = "box|cake|balloon") String theme,
    boolean bgmEnabled,
    @Pattern(regexp = "[a-z0-9-]{3,32}") String slug
) {}

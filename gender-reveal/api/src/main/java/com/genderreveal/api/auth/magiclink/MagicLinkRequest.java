package com.genderreveal.api.auth.magiclink;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MagicLinkRequest(@NotBlank @Email String email) {}

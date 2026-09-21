package com.genderreveal.api.guestbook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestbookEntryCreateRequest(
    @NotBlank @Size(max = 40) String nickname,
    @NotBlank @Size(max = 500) String message
) {}

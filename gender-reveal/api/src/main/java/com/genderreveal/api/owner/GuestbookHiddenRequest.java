package com.genderreveal.api.owner;

import jakarta.validation.constraints.NotNull;

public record GuestbookHiddenRequest(@NotNull Boolean hidden) {}

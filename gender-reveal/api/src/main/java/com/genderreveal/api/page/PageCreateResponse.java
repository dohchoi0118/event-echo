package com.genderreveal.api.page;

import java.time.Instant;
import java.time.LocalDate;

public record PageCreateResponse(
    String slug,
    String nickname,
    Instant revealAt,
    LocalDate dueDate,
    String message,
    String theme,
    boolean bgmEnabled,
    Instant createdAt,
    Instant expiresAt
) {
    static PageCreateResponse from(Page page) {
        return new PageCreateResponse(
            page.getSlug(), page.getNickname(), page.getRevealAt(), page.getDueDate(),
            page.getMessage(), page.getTheme(), page.isBgmEnabled(),
            page.getCreatedAt(), page.getExpiresAt()
        );
    }
}

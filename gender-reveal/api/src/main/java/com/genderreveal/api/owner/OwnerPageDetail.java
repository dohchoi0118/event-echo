package com.genderreveal.api.owner;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

public record OwnerPageDetail(String slug, String nickname, String actualGender, LocalDate dueDate,
                               String message, String theme, boolean bgmEnabled, String status,
                               Instant revealAt, Instant createdAt, Instant expiresAt, boolean extended) {

    static OwnerPageDetail of(Page page, PageStatus status) {
        return new OwnerPageDetail(page.getSlug(), page.getNickname(), page.getActualGender(), page.getDueDate(),
            page.getMessage(), page.getTheme(), page.isBgmEnabled(), status.name().toLowerCase(Locale.ROOT),
            page.getRevealAt(), page.getCreatedAt(), page.getExpiresAt(), page.isExtended());
    }
}

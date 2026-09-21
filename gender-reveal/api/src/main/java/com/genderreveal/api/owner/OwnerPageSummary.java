package com.genderreveal.api.owner;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageStatus;

import java.time.Instant;
import java.util.Locale;

public record OwnerPageSummary(String slug, String nickname, String status, Instant revealAt,
                                Instant expiresAt, boolean extended, String theme) {

    static OwnerPageSummary of(Page page, PageStatus status) {
        return new OwnerPageSummary(page.getSlug(), page.getNickname(), status.name().toLowerCase(Locale.ROOT),
            page.getRevealAt(), page.getExpiresAt(), page.isExtended(), page.getTheme());
    }
}

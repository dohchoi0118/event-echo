package com.genderreveal.api.page;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PageStatusCalculator {

    public PageStatus calculate(Page page, Instant now) {
        if (!now.isBefore(page.getExpiresAt())) {
            return PageStatus.EXPIRED;
        }
        // actualGender is set separately from creation (e.g. by the doctor) — stays SECRET
        // regardless of revealAt until it's actually been set.
        if (now.isBefore(page.getRevealAt()) || page.getActualGender() == null) {
            return PageStatus.SECRET;
        }
        return PageStatus.OPEN;
    }
}

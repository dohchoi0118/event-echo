package com.genderreveal.api.page;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PageStatusCalculator {

    public PageStatus calculate(Page page, Instant now) {
        if (now.isBefore(page.getRevealAt())) {
            return PageStatus.SECRET;
        }
        if (!now.isBefore(page.getExpiresAt())) {
            return PageStatus.EXPIRED;
        }
        return PageStatus.OPEN;
    }
}

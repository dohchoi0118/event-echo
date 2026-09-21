package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PageStatusCalculatorTest {

    private final PageStatusCalculator calculator = new PageStatusCalculator();

    @Test
    void secretBeforeRevealAt() {
        Instant now = Instant.now();
        Page page = pageWith(now.plus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS));

        assertThat(calculator.calculate(page, now)).isEqualTo(PageStatus.SECRET);
    }

    @Test
    void openBetweenRevealAtAndExpiresAt() {
        Instant now = Instant.now();
        Page page = pageWith(now.minus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS));

        assertThat(calculator.calculate(page, now)).isEqualTo(PageStatus.OPEN);
    }

    @Test
    void expiredAfterExpiresAt() {
        Instant now = Instant.now();
        Page page = pageWith(now.minus(31, ChronoUnit.DAYS), now.minus(1, ChronoUnit.SECONDS));

        assertThat(calculator.calculate(page, now)).isEqualTo(PageStatus.EXPIRED);
    }

    @Test
    void expiredExactlyAtBoundary() {
        Instant now = Instant.now();
        Page page = pageWith(now.minus(31, ChronoUnit.DAYS), now);

        assertThat(calculator.calculate(page, now)).isEqualTo(PageStatus.EXPIRED);
    }

    private Page pageWith(Instant revealAt, Instant expiresAt) {
        return new Page(
            "slug", "닉네임", "boy", revealAt, null, "메시지", "box", false,
            "owner@example.com", Instant.now(), expiresAt
        );
    }
}

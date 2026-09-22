package com.genderreveal.api.common;

import com.genderreveal.api.config.MutableTestClock;
import com.genderreveal.api.config.MutableTestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
class RateLimiterTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private Clock clock;

    @Test
    void allowsUpToTheLimitThenRejects() {
        String key = "guest-" + System.nanoTime();

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.allow(key, 5, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(rateLimiter.allow(key, 5, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void differentKeysAreIndependent() {
        String a = "guest-a-" + System.nanoTime();
        String b = "guest-b-" + System.nanoTime();

        for (int i = 0; i < 5; i++) {
            rateLimiter.allow(a, 5, Duration.ofMinutes(1));
        }

        assertThat(rateLimiter.allow(a, 5, Duration.ofMinutes(1))).isFalse();
        assertThat(rateLimiter.allow(b, 5, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void windowSlidesForwardAsTimePasses() {
        String key = "guest-slide-" + System.nanoTime();
        MutableTestClock testClock = (MutableTestClock) clock;

        for (int i = 0; i < 5; i++) {
            rateLimiter.allow(key, 5, Duration.ofMinutes(1));
        }
        assertThat(rateLimiter.allow(key, 5, Duration.ofMinutes(1))).isFalse();

        testClock.advanceTo(testClock.instant().plus(61, ChronoUnit.SECONDS));

        assertThat(rateLimiter.allow(key, 5, Duration.ofMinutes(1))).isTrue();
    }
}

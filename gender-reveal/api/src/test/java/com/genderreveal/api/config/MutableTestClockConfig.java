package com.genderreveal.api.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Overrides the production {@link ClockConfig} bean with a {@link MutableTestClock} so tests
 * can fast-forward time (e.g. past a page's expiresAt) instead of relying on the real clock.
 * Import into a test class with {@code @Import(MutableTestClockConfig.class)}; the resulting
 * {@code @Primary} bean shadows {@link ClockConfig#clock()} in that test's context.
 */
@TestConfiguration
public class MutableTestClockConfig {

    @Bean
    @Primary
    public Clock mutableTestClock() {
        return new MutableTestClock(Instant.now(), ZoneOffset.UTC);
    }
}

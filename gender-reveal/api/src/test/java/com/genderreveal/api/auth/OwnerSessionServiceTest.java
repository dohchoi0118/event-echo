package com.genderreveal.api.auth;

import com.genderreveal.api.config.MutableTestClock;
import com.genderreveal.api.config.MutableTestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
@Transactional
class OwnerSessionServiceTest {

    @Autowired
    private OwnerSessionService sessionService;

    @Autowired
    private OwnerSessionRepository sessionRepository;

    @Autowired
    private Clock clock;

    @Test
    void createdSessionIsFoundByRawTokenAndStoredOnlyAsHash() {
        String raw = sessionService.create(" Owner@Example.com ");

        assertThat(sessionService.findValid(raw)).get()
            .extracting(OwnerSession::getOwnerEmail).isEqualTo("owner@example.com");
        assertThat(sessionRepository.findBySessionTokenHash(raw)).isEmpty();
        assertThat(sessionRepository.findBySessionTokenHash(TokenHasher.sha256(raw))).isPresent();
    }

    @Test
    void sessionExpiresAfterSevenDays() {
        String raw = sessionService.create("owner@example.com");

        MutableTestClock testClock = (MutableTestClock) clock;
        testClock.advanceTo(testClock.instant().plus(8, ChronoUnit.DAYS));

        assertThat(sessionService.findValid(raw)).isEmpty();
    }

    @Test
    void deleteRemovesSession() {
        String raw = sessionService.create("owner@example.com");

        sessionService.delete(raw);

        assertThat(sessionService.findValid(raw)).isEmpty();
    }

    @Test
    void unknownOrBlankTokensAreInvalid() {
        assertThat(sessionService.findValid("nope")).isEmpty();
        assertThat(sessionService.findValid(null)).isEmpty();
        assertThat(sessionService.findValid("")).isEmpty();
    }
}

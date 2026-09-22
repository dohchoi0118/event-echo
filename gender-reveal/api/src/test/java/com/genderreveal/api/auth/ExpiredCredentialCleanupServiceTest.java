package com.genderreveal.api.auth;

import com.genderreveal.api.config.MutableTestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
@Transactional
class ExpiredCredentialCleanupServiceTest {

    @Autowired
    private ExpiredCredentialCleanupService cleanupService;

    @Autowired
    private MagicLinkTokenRepository tokenRepository;

    @Autowired
    private OwnerSessionRepository sessionRepository;

    @Autowired
    private Clock clock;

    @Test
    void purgesOnlyExpiredTokensAndSessions() {
        Instant now = Instant.now(clock);
        tokenRepository.save(new MagicLinkToken("expired@example.com", "hash-expired", now.minus(1, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS)));
        MagicLinkToken fresh = tokenRepository.save(new MagicLinkToken("fresh@example.com", "hash-fresh", now.plus(10, ChronoUnit.MINUTES), now));
        sessionRepository.save(new OwnerSession("expired@example.com", "sess-hash-expired", now.minus(1, ChronoUnit.DAYS), now.minus(8, ChronoUnit.DAYS)));
        OwnerSession freshSession = sessionRepository.save(new OwnerSession("fresh@example.com", "sess-hash-fresh", now.plus(7, ChronoUnit.DAYS), now));

        cleanupService.purgeExpired();

        assertThat(tokenRepository.findByTokenHash("hash-expired")).isEmpty();
        assertThat(tokenRepository.findById(fresh.getId())).isPresent();
        assertThat(sessionRepository.findBySessionTokenHash("sess-hash-expired")).isEmpty();
        assertThat(sessionRepository.findById(freshSession.getId())).isPresent();
    }
}

package com.genderreveal.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class ExpiredCredentialCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ExpiredCredentialCleanupService.class);

    private final MagicLinkTokenRepository tokenRepository;
    private final OwnerSessionRepository sessionRepository;
    private final Clock clock;

    public ExpiredCredentialCleanupService(MagicLinkTokenRepository tokenRepository,
                                            OwnerSessionRepository sessionRepository, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    /** Runs once a day; also callable directly (e.g. from tests). */
    @Scheduled(cron = "0 0 4 * * *")
    public void purgeExpired() {
        Instant now = Instant.now(clock);
        int tokens = tokenRepository.deleteByExpiresAtBefore(now);
        int sessions = sessionRepository.deleteByExpiresAtBefore(now);
        if (tokens > 0 || sessions > 0) {
            log.info("Purged {} expired magic-link token(s) and {} expired owner session(s)", tokens, sessions);
        }
    }
}

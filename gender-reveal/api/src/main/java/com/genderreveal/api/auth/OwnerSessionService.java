package com.genderreveal.api.auth;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class OwnerSessionService {

    static final Duration SESSION_TTL = Duration.ofDays(7);

    private final OwnerSessionRepository sessionRepository;
    private final Clock clock;

    public OwnerSessionService(OwnerSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    /** Creates a session for the (normalized) email and returns the raw token to put in the cookie. */
    public String create(String email) {
        Instant now = Instant.now(clock);
        String rawToken = TokenGenerator.newToken();
        sessionRepository.save(new OwnerSession(
            MagicLinkService.normalize(email), TokenHasher.sha256(rawToken), now.plus(SESSION_TTL), now));
        return rawToken;
    }

    public Optional<OwnerSession> findValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        return sessionRepository.findBySessionTokenHash(TokenHasher.sha256(rawToken))
            .filter(session -> session.getExpiresAt().isAfter(now));
    }

    public void delete(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessionRepository.findBySessionTokenHash(TokenHasher.sha256(rawToken)).ifPresent(sessionRepository::delete);
    }
}

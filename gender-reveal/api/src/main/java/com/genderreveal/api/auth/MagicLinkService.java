package com.genderreveal.api.auth;

import com.genderreveal.api.config.AppProperties;
import com.genderreveal.api.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final MagicLinkTokenRepository tokenRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final Clock clock;

    public MagicLinkService(MagicLinkTokenRepository tokenRepository, EmailSender emailSender,
                             AppProperties appProperties, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.emailSender = emailSender;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Always returns normally so callers can answer 202 regardless of outcome (no account enumeration). */
    public void request(String email) {
        String normalized = normalize(email);
        Instant now = Instant.now(clock);

        if (tokenRepository.existsByOwnerEmailAndCreatedAtAfter(normalized, now.minus(COOLDOWN))) {
            return;
        }

        String rawToken = TokenGenerator.newToken();
        tokenRepository.save(new MagicLinkToken(normalized, TokenHasher.sha256(rawToken), now.plus(TOKEN_TTL), now));

        String link = appProperties.baseUrl() + "/api/auth/callback?token=" + rawToken;
        String body = "아래 링크를 누르면 로그인돼요. 링크는 15분 동안, 한 번만 쓸 수 있어요.\n\n" + link
            + "\n\n본인이 요청하지 않았다면 이 메일을 무시해 주세요.";
        try {
            emailSender.send(normalized, "젠더리빌 로그인 링크", body);
        } catch (RuntimeException ex) {
            log.error("Failed to send magic link email", ex);
        }
    }

    /** Returns the owner email if the token is valid, unused and unexpired; consumes it atomically. */
    public Optional<String> consume(String rawToken) {
        Instant now = Instant.now(clock);
        return tokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))
            .filter(token -> !token.isUsed() && token.getExpiresAt().isAfter(now))
            .filter(token -> tokenRepository.markUsed(token.getId()) == 1)
            .map(MagicLinkToken::getOwnerEmail);
    }
}

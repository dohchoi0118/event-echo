package com.genderreveal.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface OwnerSessionRepository extends JpaRepository<OwnerSession, Long> {
    Optional<OwnerSession> findBySessionTokenHash(String sessionTokenHash);

    int deleteByExpiresAtBefore(Instant cutoff);
}

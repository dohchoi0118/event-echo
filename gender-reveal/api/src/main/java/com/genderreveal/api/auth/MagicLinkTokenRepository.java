package com.genderreveal.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, Long> {

    Optional<MagicLinkToken> findByTokenHash(String tokenHash);

    boolean existsByOwnerEmailAndCreatedAtAfter(String ownerEmail, Instant cutoff);

    /** Atomically consumes a token: returns 1 only for the single caller that flips used from false to true. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MagicLinkToken t set t.used = true where t.id = :id and t.used = false")
    int markUsed(@Param("id") Long id);
}

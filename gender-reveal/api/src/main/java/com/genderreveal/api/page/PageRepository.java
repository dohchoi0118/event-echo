package com.genderreveal.api.page;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {
    Optional<Page> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Page> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

    /** Atomically flips extended false→true; returns 0 if already extended (lost the race or a repeat call). */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Page p set p.expiresAt = :newExpiresAt, p.extended = true where p.id = :id and p.extended = false")
    int markExtended(@Param("id") Long id, @Param("newExpiresAt") Instant newExpiresAt);
}

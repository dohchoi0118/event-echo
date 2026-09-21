package com.genderreveal.api.guess;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuessRepository extends JpaRepository<Guess, Long> {
    boolean existsByPageIdAndGuestCookieId(Long pageId, String guestCookieId);

    Optional<Guess> findByPageIdAndGuestCookieId(Long pageId, String guestCookieId);
}

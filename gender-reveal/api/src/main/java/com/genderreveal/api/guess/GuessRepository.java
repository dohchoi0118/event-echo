package com.genderreveal.api.guess;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuessRepository extends JpaRepository<Guess, Long> {
    boolean existsByPageIdAndGuestCookieId(Long pageId, String guestCookieId);
}

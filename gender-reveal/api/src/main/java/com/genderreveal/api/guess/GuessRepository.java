package com.genderreveal.api.guess;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuessRepository extends JpaRepository<Guess, Long> {
    boolean existsByPageIdAndGuestCookieId(Long pageId, String guestCookieId);

    Optional<Guess> findByPageIdAndGuestCookieId(Long pageId, String guestCookieId);

    long countByPageId(Long pageId);

    long countByPageIdAndGuessedGender(Long pageId, String guessedGender);

    List<Guess> findByPageId(Long pageId);
}

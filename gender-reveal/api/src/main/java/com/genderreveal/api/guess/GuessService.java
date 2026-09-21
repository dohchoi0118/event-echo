package com.genderreveal.api.guess;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageNotFoundException;
import com.genderreveal.api.page.PageNotOpenException;
import com.genderreveal.api.page.PageRepository;
import com.genderreveal.api.page.PageStatus;
import com.genderreveal.api.page.PageStatusCalculator;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class GuessService {

    private final GuessRepository guessRepository;
    private final PageRepository pageRepository;
    private final PageStatusCalculator statusCalculator;
    private final Clock clock;

    public GuessService(GuessRepository guessRepository, PageRepository pageRepository,
                         PageStatusCalculator statusCalculator, Clock clock) {
        this.guessRepository = guessRepository;
        this.pageRepository = pageRepository;
        this.statusCalculator = statusCalculator;
        this.clock = clock;
    }

    public Guess create(String slug, String guestCookieId, String guessedGender) {
        Page page = pageRepository.findBySlug(slug)
            .orElseThrow(() -> new PageNotFoundException(slug));

        Instant now = Instant.now(clock);
        PageStatus status = statusCalculator.calculate(page, now);
        if (status != PageStatus.OPEN) {
            throw new PageNotOpenException(slug);
        }

        Optional<Guess> existing = guessRepository.findByPageIdAndGuestCookieId(page.getId(), guestCookieId);
        if (existing.isPresent()) {
            throw new DuplicateGuessException(slug, existing.get().getGuessedGender());
        }

        Guess guess = new Guess(page.getId(), guestCookieId, guessedGender, now);
        try {
            return guessRepository.save(guess);
        } catch (DataAccessException ex) {
            // TOCTOU backstop: another request inserted the same (page_id, guest_cookie_id) between
            // the check above and this save. Catches DataAccessException, not the narrower
            // DataIntegrityViolationException, because the SQLite dialect reports no SQLState, so
            // the unique-constraint violation surfaces as JpaSystemException (a DataAccessException
            // but NOT a DataIntegrityViolationException). This also covers other persistence
            // failures on this single-row insert; guessedGender is pattern-validated to boy|girl,
            // so the unique constraint is the only realistic cause. Revisit if that changes.
            String existingGuessedGender = guessRepository.findByPageIdAndGuestCookieId(page.getId(), guestCookieId)
                .map(Guess::getGuessedGender)
                .orElse(null);
            throw new DuplicateGuessException(slug, existingGuessedGender);
        }
    }
}

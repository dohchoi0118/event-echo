package com.genderreveal.api.guess;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageNotFoundException;
import com.genderreveal.api.page.PageNotOpenException;
import com.genderreveal.api.page.PageRepository;
import com.genderreveal.api.page.PageStatus;
import com.genderreveal.api.page.PageStatusCalculator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

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

        if (guessRepository.existsByPageIdAndGuestCookieId(page.getId(), guestCookieId)) {
            throw new DuplicateGuessException(slug);
        }

        Guess guess = new Guess(page.getId(), guestCookieId, guessedGender, now);
        try {
            return guessRepository.save(guess);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateGuessException(slug);
        }
    }
}

package com.genderreveal.api.guess;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Simulates the TOCTOU race window in {@link GuessService#create}: the upfront duplicate
 * check reports "absent" (as if another request had not committed yet), but by the time
 * save() runs the conflicting row exists, so the REAL SQLite UNIQUE constraint fires. The
 * repository is a spy over the real database, so the exception that reaches the service's
 * backstop catch is the genuine one, not a stubbed type.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GuessServiceRaceTest {

    @Autowired
    private GuessService guessService;

    @Autowired
    private PageRepository pageRepository;

    @SpyBean
    private GuessRepository guessRepository;

    @Test
    void raceLosingInsertIsMappedToDuplicateGuessException() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page page = pageRepository.save(new Page(
            "guess-race-" + System.nanoTime(), "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "owner@example.com", now, now.plus(30, ChronoUnit.DAYS)));

        // The "winner" of the race already inserted its row.
        Guess winner = guessRepository.save(new Guess(page.getId(), "racing-guest", "boy", now));

        // Upfront check (first lookup) misses it; the backstop's re-lookup sees the winner's row.
        doReturn(Optional.empty()).doReturn(Optional.of(winner))
            .when(guessRepository).findByPageIdAndGuestCookieId(any(), any());

        assertThatThrownBy(() -> guessService.create(page.getSlug(), "racing-guest", "girl"))
            .isInstanceOf(DuplicateGuessException.class)
            .satisfies(ex -> assertThat(((DuplicateGuessException) ex).getExistingGuessedGender())
                .isEqualTo("boy"));
    }
}

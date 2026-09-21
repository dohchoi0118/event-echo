package com.genderreveal.api.guess;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class GuessRepositoryTest {

    @Autowired
    private GuessRepository guessRepository;

    @Autowired
    private PageRepository pageRepository;

    @Test
    void savesAndDetectsExistingGuestGuess() {
        Page page = pageRepository.save(samplePage());

        Guess guess = new Guess(page.getId(), "guest-abc", "boy", Instant.now());
        guessRepository.save(guess);

        assertThat(guessRepository.existsByPageIdAndGuestCookieId(page.getId(), "guest-abc")).isTrue();
    }

    @Test
    void existsByPageIdAndGuestCookieIdReflectsUnsavedCombination() {
        Page page = pageRepository.save(samplePage());

        assertThat(guessRepository.existsByPageIdAndGuestCookieId(page.getId(), "nobody")).isFalse();
    }

    @Test
    void uniqueConstraintOnPageIdAndGuestCookieIdRejectsDuplicateAtDbLevel() {
        Page page = pageRepository.save(samplePage());

        guessRepository.save(new Guess(page.getId(), "guest-dup", "boy", Instant.now()));
        guessRepository.flush();

        Guess duplicate = new Guess(page.getId(), "guest-dup", "girl", Instant.now());

        // Caught broadly as DataAccessException (rather than the narrower
        // DataIntegrityViolationException) because Hibernate's SQLite community dialect
        // surfaces this constraint violation without a recognizable SQLState, so Spring's
        // exception translation falls back to the generic JpaSystemException rather than
        // classifying it as a DataIntegrityViolationException.
        assertThatThrownBy(() -> {
            guessRepository.save(duplicate);
            guessRepository.flush();
        }).isInstanceOf(DataAccessException.class);
    }

    private Page samplePage() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new Page(
            "guess-test-" + System.nanoTime(), "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "owner@example.com", now, now.plus(30, ChronoUnit.DAYS)
        );
    }
}

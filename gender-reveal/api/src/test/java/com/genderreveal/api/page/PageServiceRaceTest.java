package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

/**
 * Simulates the TOCTOU race window in {@link PageService#create}: the upfront slug check
 * reports "free", but by the time save() runs another request has taken it, so the REAL
 * SQLite UNIQUE(slug) constraint fires. The repository is a spy over the real database, so
 * the exception reaching the service's backstop catch is the genuine one.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PageServiceRaceTest {

    @Autowired
    private PageService pageService;

    @SpyBean
    private PageRepository pageRepository;

    @Test
    void raceLosingInsertIsMappedToSlugAlreadyTakenException() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        pageRepository.save(new Page(
            "race-slug", "뽀튼이", "boy", now.plus(1, ChronoUnit.DAYS),
            null, "메시지", "box", false, "owner@example.com", now, now.plus(30, ChronoUnit.DAYS)));

        // Upfront check misses the row the race winner already inserted.
        doReturn(false).when(pageRepository).existsBySlug(anyString());

        PageCreateRequest request = new PageCreateRequest(
            "뽀튼이", "boy", now.plus(1, ChronoUnit.DAYS), null, "메시지", "box", false,
            "owner@example.com", "race-slug");

        assertThatThrownBy(() -> pageService.create(request))
            .isInstanceOf(SlugAlreadyTakenException.class);
    }
}

package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PageRepositoryTest {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndFindsBySlug() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page page = new Page(
            "ppo-2026", "뽀튼이", "boy", now.plus(1, ChronoUnit.DAYS),
            null, "왕자님이 찾아왔어요!", "box", true,
            "owner@example.com", now, now.plus(30, ChronoUnit.DAYS)
        );

        pageRepository.save(page);

        Optional<Page> found = pageRepository.findBySlug("ppo-2026");

        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("뽀튼이");
        assertThat(found.get().getActualGender()).isEqualTo("boy");
    }

    @Test
    void existsBySlugReflectsSavedPages() {
        assertThat(pageRepository.existsBySlug("missing-slug")).isFalse();
    }

    @Test
    void storesInstantColumnsAsIso8601TextNotEpochMillis() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page page = new Page(
            "iso-instant-check", "뽀튼이", "boy", now.plus(1, ChronoUnit.DAYS),
            null, "왕자님이 찾아왔어요!", "box", true,
            "owner@example.com", now, now.plus(30, ChronoUnit.DAYS)
        );

        pageRepository.save(page);

        String storedRevealAt = jdbcTemplate.queryForObject(
            "SELECT reveal_at FROM pages WHERE slug = ?", String.class, "iso-instant-check");
        String storedCreatedAt = jdbcTemplate.queryForObject(
            "SELECT created_at FROM pages WHERE slug = ?", String.class, "iso-instant-check");
        String storedExpiresAt = jdbcTemplate.queryForObject(
            "SELECT expires_at FROM pages WHERE slug = ?", String.class, "iso-instant-check");

        String iso8601Pattern = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";
        assertThat(storedRevealAt).matches(iso8601Pattern);
        assertThat(storedCreatedAt).matches(iso8601Pattern);
        assertThat(storedExpiresAt).matches(iso8601Pattern);
    }
}

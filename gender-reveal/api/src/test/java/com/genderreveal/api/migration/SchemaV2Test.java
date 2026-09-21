package com.genderreveal.api.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaV2Test {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerSessionsAreKeyedByEmailNotPage() {
        assertThat(columns("owner_sessions"))
            .contains("owner_email", "session_token_hash", "expires_at", "created_at")
            .doesNotContain("page_id");
    }

    @Test
    void magicLinkTokensNoLongerReferenceAPage() {
        assertThat(columns("magic_link_tokens"))
            .contains("owner_email", "token_hash", "expires_at", "used", "created_at")
            .doesNotContain("page_id");
    }

    @Test
    void guestbookEntriesHaveNullableGuestCookieId() {
        assertThat(columns("guestbook_entries")).contains("guest_cookie_id");
    }

    @Test
    void pageVisitsRejectsOrphanPageId() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO page_visits (page_id, guest_cookie_id, created_at) VALUES (999999, 'g', '2026-01-01T00:00:00.000Z')"))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("FOREIGN KEY");
    }

    @Test
    void pageVisitsRejectsDuplicateVisitor() {
        // Insert a real page
        jdbcTemplate.update(
            "INSERT INTO pages (slug, nickname, actual_gender, reveal_at, message, theme, owner_email, created_at, expires_at) " +
            "VALUES ('test-slug', 'test-nick', 'boy', '2026-01-01T12:00:00.000Z', NULL, 'box', 'owner@test.com', '2026-01-01T00:00:00.000Z', '2026-02-01T00:00:00.000Z')");

        // Insert first visit
        jdbcTemplate.update(
            "INSERT INTO page_visits (page_id, guest_cookie_id, created_at) VALUES (1, 'guest-cookie-1', '2026-01-01T00:00:00.000Z')");

        // Insert duplicate visit from same guest to same page
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO page_visits (page_id, guest_cookie_id, created_at) VALUES (1, 'guest-cookie-1', '2026-01-01T01:00:00.000Z')"))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("UNIQUE");
    }

    private List<String> columns(String table) {
        return jdbcTemplate.queryForList("SELECT name FROM pragma_table_info('" + table + "')", String.class);
    }
}

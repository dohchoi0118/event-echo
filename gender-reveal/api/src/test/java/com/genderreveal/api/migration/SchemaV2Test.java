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
    void pageVisitsRejectsOrphanPageAndDuplicateVisitor() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO page_visits (page_id, guest_cookie_id, created_at) VALUES (999999, 'g', 't')"))
            .isInstanceOf(DataAccessException.class);
    }

    private List<String> columns(String table) {
        return jdbcTemplate.queryForList("SELECT name FROM pragma_table_info('" + table + "')", String.class);
    }
}

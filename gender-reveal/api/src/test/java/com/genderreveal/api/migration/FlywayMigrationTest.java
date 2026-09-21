package com.genderreveal.api.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allExpectedTablesExist() {
        List<String> tableNames = jdbcTemplate.queryForList(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'flyway_schema_history'",
            String.class
        );

        assertThat(tableNames).containsExactlyInAnyOrder(
            "pages", "guesses", "guestbook_entries", "magic_link_tokens", "owner_sessions", "page_visits"
        );
    }
}

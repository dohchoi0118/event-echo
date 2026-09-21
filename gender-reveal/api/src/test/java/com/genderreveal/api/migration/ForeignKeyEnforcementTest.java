package com.genderreveal.api.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ForeignKeyEnforcementTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsGuessReferencingNonExistentPage() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO guesses (page_id, guest_cookie_id, guessed_gender, created_at) VALUES (?, ?, ?, ?)",
                999999L, "cookie-1", "boy", Instant.now().toString()
            ))
            .isInstanceOf(DataAccessException.class);
    }
}

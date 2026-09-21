package com.genderreveal.api.page;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.auth.OwnerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PageControllerCreateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerTestSupport ownerTestSupport;

    @Autowired
    private PageRepository pageRepository;

    @Test
    void createsPageAndReturnsGeneratedSlug() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").isNotEmpty())
            .andExpect(jsonPath("$.nickname").value("뽀튼이"));
    }

    @Test
    void rejectsInvalidGender() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "unknown",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsRevealAtBeyondRetentionWindow() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(40, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsRevealAtFarInThePastAndOpensImmediately() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().minus(40, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());
    }

    @Test
    void rejectsDuplicateCustomSlug() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "slug", "duplicate-slug"
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict());
    }

    @Test
    void createWithoutSessionIs401() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true
        );

        mockMvc.perform(post("/api/pages")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerEmailComesFromSession() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "slug", "session-owner-slug"
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("Real-Owner@Example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        assertThat(pageRepository.findBySlug("session-owner-slug").orElseThrow().getOwnerEmail())
            .isEqualTo("real-owner@example.com");
    }

    @Test
    void ownerEmailInRequestBodyIsIgnored() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "slug", "spoof-attempt-slug",
            "ownerEmail", "victim@example.com"
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("attacker@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        assertThat(pageRepository.findBySlug("spoof-attempt-slug").orElseThrow().getOwnerEmail())
            .isEqualTo("attacker@example.com");
    }
}

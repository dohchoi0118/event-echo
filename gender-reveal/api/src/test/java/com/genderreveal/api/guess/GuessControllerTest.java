package com.genderreveal.api.guess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.auth.OwnerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GuessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerTestSupport ownerTestSupport;

    @Test
    void firstGuessIssuesCookieAndSucceeds() throws Exception {
        String slug = createOpenPage("guess-first-slug");

        MvcResult result = mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isCreated())
            .andExpect(cookie().exists("guest_id"))
            .andExpect(jsonPath("$.guessedGender").value("boy"))
            .andReturn();

        String guestId = result.getResponse().getCookie("guest_id").getValue();
        assertThat(guestId).isNotBlank();
    }

    @Test
    void secondGuessWithSameCookieIsRejected() throws Exception {
        String slug = createOpenPage("guess-dup-slug");
        MockCookie cookie = new MockCookie("guest_id", UUID.randomUUID().toString());

        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .cookie(cookie)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .cookie(cookie)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "girl"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.guessedGender").value("boy"));
    }

    @Test
    void malformedCookieValueIsReplacedWithFreshUuid() throws Exception {
        String slug = createOpenPage("guess-malformed-cookie-slug");
        MockCookie cookie = new MockCookie("guest_id", "not-a-uuid");

        MvcResult result = mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .cookie(cookie)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isCreated())
            .andReturn();

        String returnedGuestId = result.getResponse().getCookie("guest_id").getValue();
        assertThat(returnedGuestId).isNotEqualTo("not-a-uuid");
        assertThat(UUID.fromString(returnedGuestId)).isNotNull();
    }

    @Test
    void guessOnSecretPageIsRejected() throws Exception {
        String slug = createPage("guess-secret-slug", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidGuessedGender() throws Exception {
        String slug = createOpenPage("guess-invalid-gender-slug");

        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "other"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void guessOnUnknownSlugReturns404() throws Exception {
        mockMvc.perform(post("/api/pages/does-not-exist/guesses")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void sixthGuessRequestFromTheSameGuestWithinAMinuteIsRateLimited() throws Exception {
        String slug = createOpenPage("rate-limit-guess-slug");
        MockCookie cookie = new MockCookie("guest_id", UUID.randomUUID().toString());
        // First guess succeeds (201); the other 4 hit DuplicateGuess (409) but each still counts
        // toward the limit, since the rate check runs before the duplicate check.
        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .cookie(cookie).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isCreated());
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                    .cookie(cookie).contentType("application/json")
                    .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
                .andExpect(status().isConflict());
        }

        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .cookie(cookie).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
    }

    private String createOpenPage(String slug) throws Exception {
        return createPage(slug, Instant.now().minus(1, ChronoUnit.HOURS));
    }

    private String createPage(String slug, Instant revealAt) throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", revealAt.toString(),
            "theme", "box",
            "bgmEnabled", true,
            "slug", slug
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        return slug;
    }
}

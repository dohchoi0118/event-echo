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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PageControllerGetTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerTestSupport ownerTestSupport;

    @Test
    void returnsSecretStatusBeforeRevealAt() throws Exception {
        String slug = createPage("future-slug", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/pages/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("secret"))
            .andExpect(jsonPath("$.actualGender").doesNotExist());
    }

    @Test
    void returnsOpenStatusAndActualGenderAfterRevealAt() throws Exception {
        String slug = createPage("past-slug", Instant.now().minus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/pages/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("open"))
            .andExpect(jsonPath("$.actualGender").value("boy"));
    }

    @Test
    void returnsOpenStatusWhenRevealAtIsFarInThePast() throws Exception {
        String slug = createPage("ancient-slug", Instant.now().minus(40, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/pages/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("open"))
            .andExpect(jsonPath("$.actualGender").value("boy"));
    }

    @Test
    void setsNoStoreCacheControlHeader() throws Exception {
        String slug = createPage("cache-header-slug", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/pages/" + slug))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void returns404ForUnknownSlug() throws Exception {
        mockMvc.perform(get("/api/pages/does-not-exist"))
            .andExpect(status().isNotFound());
    }

    @Test
    void reportsSlugAvailability() throws Exception {
        String slug = createPage("availability-taken-slug", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/pages/" + slug + "/availability"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get("/api/pages/availability-free-slug/availability"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true));
    }

    private String createPage(String slug, Instant revealAt) throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
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

        // Actual gender is set separately (e.g. by the doctor), never on the create form itself —
        // set it here so the open-status assertions in this file still exercise a real open page.
        mockMvc.perform(post("/api/owner/pages/" + slug + "/actual-gender")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content("{\"actualGender\":\"boy\"}"))
            .andExpect(status().isOk());

        return slug;
    }
}

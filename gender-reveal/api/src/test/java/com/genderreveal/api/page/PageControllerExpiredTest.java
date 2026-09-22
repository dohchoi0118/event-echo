package com.genderreveal.api.page;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.auth.OwnerTestSupport;
import com.genderreveal.api.config.MutableTestClock;
import com.genderreveal.api.config.MutableTestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises GET /api/pages/{slug} once a page has genuinely passed its retention window.
 * ClockConfig's Clock.systemUTC() bean offers no test seam for fast-forwarding wall-clock
 * time, so this uses a dedicated Spring context wired with MutableTestClockConfig instead.
 * Kept as its own test class (rather than added to PageControllerGetTest) to isolate the clock
 * advance from other tests' secret/open assertions. The Clock bean is a shared singleton across
 * tests that reuse this Spring context, so an advance here is not literally invisible to them —
 * but every test derives its own base time from clock.instant() at the point it runs, rather than
 * assuming wall-clock time, so the actual assertions stay correct regardless of test order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
@Transactional
class PageControllerExpiredTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerTestSupport ownerTestSupport;

    @Autowired
    private Clock clock;

    @Test
    void returnsExpiredStatusOncePastRetentionWindow() throws Exception {
        assertThat(clock).isInstanceOf(MutableTestClock.class);
        MutableTestClock testClock = (MutableTestClock) clock;
        Instant start = testClock.instant();

        String slug = createPage("expiring-page", start.plus(1, ChronoUnit.HOURS));

        testClock.advanceTo(start.plus(31, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/pages/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("expired"))
            .andExpect(jsonPath("$.nickname").value("뽀튼이"))
            .andExpect(jsonPath("$.actualGender").doesNotExist())
            .andExpect(jsonPath("$.dueDate").doesNotExist())
            .andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(jsonPath("$.theme").doesNotExist())
            .andExpect(jsonPath("$.bgmEnabled").doesNotExist());
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

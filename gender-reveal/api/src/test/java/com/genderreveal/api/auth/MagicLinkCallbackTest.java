package com.genderreveal.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.config.MutableTestClock;
import com.genderreveal.api.config.MutableTestClockConfig;
import com.genderreveal.api.email.RecordingEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
@Transactional
class MagicLinkCallbackTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emails;

    @Autowired
    private Clock clock;

    @BeforeEach
    void resetEmails() {
        emails.clear();
    }

    @Test
    void validTokenCreatesSessionCookieAndRedirectsToDashboard() throws Exception {
        String token = requestTokenFor("cb-a@example.com");

        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/callback").param("token", token))
            .andExpect(status().isFound())
            .andReturn().getResponse();

        assertThat(response.getHeader("Location")).isEqualTo("http://localhost:8080/dashboard");
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("owner_session=").contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void tokenIsSingleUse() throws Exception {
        String token = requestTokenFor("cb-b@example.com");

        mockMvc.perform(get("/api/auth/callback").param("token", token)).andExpect(status().isFound());
        MockHttpServletResponse second = mockMvc.perform(get("/api/auth/callback").param("token", token))
            .andExpect(status().isFound())
            .andReturn().getResponse();

        assertThat(second.getHeader("Location")).isEqualTo("http://localhost:8080/login?error=invalid");
        assertThat(second.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = requestTokenFor("cb-c@example.com");

        MutableTestClock testClock = (MutableTestClock) clock;
        testClock.advanceTo(testClock.instant().plus(16, ChronoUnit.MINUTES));

        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/callback").param("token", token))
            .andExpect(status().isFound())
            .andReturn().getResponse();

        assertThat(response.getHeader("Location")).isEqualTo("http://localhost:8080/login?error=invalid");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/callback").param("token", "garbage"))
            .andExpect(status().isFound())
            .andReturn().getResponse();

        assertThat(response.getHeader("Location")).isEqualTo("http://localhost:8080/login?error=invalid");
    }

    private String requestTokenFor(String email) throws Exception {
        mockMvc.perform(post("/api/auth/magic-link")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("email", email))))
            .andExpect(status().isAccepted());
        Matcher matcher = TOKEN.matcher(emails.sent().get(0).body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}

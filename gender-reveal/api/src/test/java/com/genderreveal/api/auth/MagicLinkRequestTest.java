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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
@Transactional
class MagicLinkRequestTest {

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
    void sendsLoginLinkToNormalizedEmail() throws Exception {
        requestLink("Owner-A@Example.com").andExpect(status().isAccepted());

        assertThat(emails.sent()).hasSize(1);
        RecordingEmailSender.SentEmail mail = emails.sent().get(0);
        assertThat(mail.to()).isEqualTo("owner-a@example.com");
        assertThat(mail.body()).contains("/api/auth/callback?token=");
    }

    @Test
    void invalidEmailIsRejectedAndNothingIsSent() throws Exception {
        requestLink("not-an-email").andExpect(status().isBadRequest());

        assertThat(emails.sent()).isEmpty();
    }

    @Test
    void secondRequestWithinCooldownSendsNothingButStillReturns202() throws Exception {
        requestLink("owner-b@example.com").andExpect(status().isAccepted());
        requestLink("owner-b@example.com").andExpect(status().isAccepted());

        assertThat(emails.sent()).hasSize(1);
    }

    @Test
    void requestAfterCooldownSendsAgain() throws Exception {
        requestLink("owner-c@example.com").andExpect(status().isAccepted());

        MutableTestClock testClock = (MutableTestClock) clock;
        testClock.advanceTo(testClock.instant().plus(61, ChronoUnit.SECONDS));
        requestLink("owner-c@example.com").andExpect(status().isAccepted());

        assertThat(emails.sent()).hasSize(2);
    }

    private org.springframework.test.web.servlet.ResultActions requestLink(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/magic-link")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }
}

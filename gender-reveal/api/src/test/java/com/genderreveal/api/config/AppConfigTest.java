package com.genderreveal.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.email.LoggingEmailSender;
import com.genderreveal.api.email.ResendEmailSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    private final AppConfig config = new AppConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void blankApiKeyUsesLoggingSender() {
        AppProperties props = new AppProperties("http://x", false, new AppProperties.Resend("", "f", "http://u"));

        assertThat(config.emailSender(props, objectMapper)).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    void configuredApiKeyUsesResendSender() {
        AppProperties props = new AppProperties("http://x", false, new AppProperties.Resend("re_key", "f", "http://u"));

        assertThat(config.emailSender(props, objectMapper)).isInstanceOf(ResendEmailSender.class);
    }
}

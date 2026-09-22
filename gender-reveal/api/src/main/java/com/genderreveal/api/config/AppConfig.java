package com.genderreveal.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.email.EmailSender;
import com.genderreveal.api.email.LoggingEmailSender;
import com.genderreveal.api.email.ResendEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public EmailSender emailSender(AppProperties props, ObjectMapper objectMapper) {
        AppProperties.Resend resend = props.resend();
        if (resend == null || resend.apiKey() == null || resend.apiKey().isBlank()) {
            log.warn("RESEND_API_KEY is not set; falling back to LoggingEmailSender — magic-link login "
                + "links will be written to the application log instead of emailed (dev-only fallback).");
            return new LoggingEmailSender();
        }
        return new ResendEmailSender(resend.apiUrl(), resend.apiKey(), resend.from(), objectMapper);
    }
}

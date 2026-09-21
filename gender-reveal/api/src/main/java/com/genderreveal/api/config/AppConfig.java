package com.genderreveal.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.email.EmailSender;
import com.genderreveal.api.email.LoggingEmailSender;
import com.genderreveal.api.email.ResendEmailSender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    @Bean
    public EmailSender emailSender(AppProperties props, ObjectMapper objectMapper) {
        AppProperties.Resend resend = props.resend();
        if (resend == null || resend.apiKey() == null || resend.apiKey().isBlank()) {
            return new LoggingEmailSender();
        }
        return new ResendEmailSender(resend.apiUrl(), resend.apiKey(), resend.from(), objectMapper);
    }
}

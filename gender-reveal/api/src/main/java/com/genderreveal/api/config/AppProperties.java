package com.genderreveal.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl, boolean sessionCookieSecure, Resend resend) {

    public record Resend(String apiKey, String from, String apiUrl) {}
}

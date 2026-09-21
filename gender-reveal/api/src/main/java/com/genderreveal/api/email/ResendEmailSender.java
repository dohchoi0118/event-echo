package com.genderreveal.api.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ResendEmailSender implements EmailSender {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String apiUrl;
    private final String apiKey;
    private final String from;
    private final ObjectMapper objectMapper;

    public ResendEmailSender(String apiUrl, String apiKey, String from, ObjectMapper objectMapper) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.from = from;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(String to, String subject, String textBody) {
        String json;
        try {
            json = objectMapper.writeValueAsString(
                Map.of("from", from, "to", List.of(to), "subject", subject, "text", textBody));
        } catch (JsonProcessingException ex) {
            throw new EmailSendException("Failed to serialize email payload", ex);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new EmailSendException("Resend responded with status " + response.statusCode());
            }
        } catch (IOException ex) {
            throw new EmailSendException("Failed to call Resend", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Interrupted while calling Resend", ex);
        }
    }
}

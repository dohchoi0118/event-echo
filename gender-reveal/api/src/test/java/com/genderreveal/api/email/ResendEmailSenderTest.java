package com.genderreveal.api.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailSenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsJsonWithBearerKeyToResend() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        String url = startServer(200, authorization, body);

        new ResendEmailSender(url, "re_test_key", "젠더리빌 <noreply@example.com>", objectMapper)
            .send("owner@example.com", "제목", "본문");

        assertThat(authorization.get()).isEqualTo("Bearer re_test_key");
        JsonNode json = objectMapper.readTree(body.get());
        assertThat(json.get("from").asText()).isEqualTo("젠더리빌 <noreply@example.com>");
        assertThat(json.get("to").get(0).asText()).isEqualTo("owner@example.com");
        assertThat(json.get("subject").asText()).isEqualTo("제목");
        assertThat(json.get("text").asText()).isEqualTo("본문");
    }

    @Test
    void nonSuccessResponseBecomesEmailSendException() throws Exception {
        String url = startServer(500, new AtomicReference<>(), new AtomicReference<>());

        ResendEmailSender sender = new ResendEmailSender(url, "k", "f@example.com", objectMapper);

        assertThatThrownBy(() -> sender.send("owner@example.com", "s", "b"))
            .isInstanceOf(EmailSendException.class);
    }

    private String startServer(int status, AtomicReference<String> authorization, AtomicReference<String> body)
            throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/emails", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/emails";
    }
}

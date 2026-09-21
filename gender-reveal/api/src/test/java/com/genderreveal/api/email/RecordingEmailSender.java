package com.genderreveal.api.email;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Test-only sender that records instead of sending. Tests call clear() in @BeforeEach. */
@Component
@Primary
public class RecordingEmailSender implements EmailSender {

    public record SentEmail(String to, String subject, String body) {}

    private final List<SentEmail> sent = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(String to, String subject, String textBody) {
        sent.add(new SentEmail(to, subject, textBody));
    }

    public List<SentEmail> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}

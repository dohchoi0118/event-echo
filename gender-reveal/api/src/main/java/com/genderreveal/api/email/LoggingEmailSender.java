package com.genderreveal.api.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dev fallback used when no Resend API key is configured. Never use in production: it logs message bodies (login links). */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String textBody) {
        log.info("[email not sent — no RESEND_API_KEY] to={} subject={}\n{}", to, subject, textBody);
    }
}

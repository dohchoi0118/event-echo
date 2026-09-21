package com.genderreveal.api.email;

public interface EmailSender {
    void send(String to, String subject, String textBody);
}

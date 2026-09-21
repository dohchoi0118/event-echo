package com.genderreveal.api.common;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** Anonymous guest identity: a random UUID in an httpOnly cookie. Not a login. */
@Component
public class GuestCookie {

    public static final String NAME = "guest_id";

    public boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String resolve(String existing) {
        return isValid(existing) ? existing : UUID.randomUUID().toString();
    }

    public String toSetCookie(String guestId) {
        return ResponseCookie.from(NAME, guestId)
            .path("/")
            .maxAge(Duration.ofDays(365))
            .httpOnly(true)
            .sameSite("Lax")
            .build()
            .toString();
    }
}

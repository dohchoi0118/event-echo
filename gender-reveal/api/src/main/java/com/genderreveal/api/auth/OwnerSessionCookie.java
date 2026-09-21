package com.genderreveal.api.auth;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class OwnerSessionCookie {

    public static final String NAME = "owner_session";

    private OwnerSessionCookie() {}

    public static String issue(String rawToken, boolean secure) {
        return ResponseCookie.from(NAME, rawToken)
            .path("/")
            .maxAge(OwnerSessionService.SESSION_TTL)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .build()
            .toString();
    }

    public static String clear(boolean secure) {
        return ResponseCookie.from(NAME, "")
            .path("/")
            .maxAge(Duration.ZERO)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .build()
            .toString();
    }
}

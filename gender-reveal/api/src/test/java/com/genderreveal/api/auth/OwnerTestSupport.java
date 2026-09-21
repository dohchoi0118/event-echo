package com.genderreveal.api.auth;

import org.springframework.mock.web.MockCookie;
import org.springframework.stereotype.Component;

/** Test-only helper: logs an owner in directly (no email round-trip) and returns the session cookie. */
@Component
public class OwnerTestSupport {

    private final OwnerSessionService sessionService;

    public OwnerTestSupport(OwnerSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public MockCookie cookieFor(String email) {
        return new MockCookie(OwnerSessionCookie.NAME, sessionService.create(email));
    }
}

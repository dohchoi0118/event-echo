package com.genderreveal.api.auth.session;

public record OwnerPrincipal(String email) {
    static final String REQUEST_ATTRIBUTE = OwnerPrincipal.class.getName();
}

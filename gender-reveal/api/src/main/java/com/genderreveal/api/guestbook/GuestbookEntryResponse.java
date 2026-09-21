package com.genderreveal.api.guestbook;

import java.time.Instant;

public record GuestbookEntryResponse(String nickname, String message, Instant createdAt) {
    static GuestbookEntryResponse from(GuestbookEntry entry) {
        return new GuestbookEntryResponse(entry.getNickname(), entry.getMessage(), entry.getCreatedAt());
    }
}

package com.genderreveal.api.guestbook;

public class GuestbookEntryNotFoundException extends RuntimeException {
    public GuestbookEntryNotFoundException(Long entryId) {
        super("Guestbook entry not found: " + entryId);
    }
}

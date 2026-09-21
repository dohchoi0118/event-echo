package com.genderreveal.api.owner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.genderreveal.api.guess.Guess;
import com.genderreveal.api.guestbook.GuestbookEntry;
import com.genderreveal.api.page.Page;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OwnerGuestbookEntryResponse(Long id, String nickname, String message, Instant createdAt,
                                           boolean hidden, String guessedGender, Boolean guessCorrect) {

    static OwnerGuestbookEntryResponse of(GuestbookEntry entry, Page page, Guess guess) {
        String guessedGender = guess == null ? null : guess.getGuessedGender();
        Boolean correct = guess == null ? null : guess.getGuessedGender().equals(page.getActualGender());
        return new OwnerGuestbookEntryResponse(entry.getId(), entry.getNickname(), entry.getMessage(),
            entry.getCreatedAt(), entry.isHidden(), guessedGender, correct);
    }
}

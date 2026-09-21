package com.genderreveal.api.owner;

import com.genderreveal.api.guess.Guess;
import com.genderreveal.api.guess.GuessRepository;
import com.genderreveal.api.guestbook.GuestbookEntryRepository;
import com.genderreveal.api.page.Page;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OwnerGuestbookService {

    private final OwnerPageService ownerPageService;
    private final GuestbookEntryRepository guestbookEntryRepository;
    private final GuessRepository guessRepository;

    public OwnerGuestbookService(OwnerPageService ownerPageService, GuestbookEntryRepository guestbookEntryRepository,
                                  GuessRepository guessRepository) {
        this.ownerPageService = ownerPageService;
        this.guestbookEntryRepository = guestbookEntryRepository;
        this.guessRepository = guessRepository;
    }

    public List<OwnerGuestbookEntryResponse> list(String slug, String ownerEmail) {
        Page page = ownerPageService.requireOwned(slug, ownerEmail);

        Map<String, Guess> guessesByGuest = new HashMap<>();
        for (Guess guess : guessRepository.findByPageId(page.getId())) {
            guessesByGuest.put(guess.getGuestCookieId(), guess);
        }

        return guestbookEntryRepository.findByPageIdOrderByCreatedAtDesc(page.getId()).stream()
            .map(entry -> OwnerGuestbookEntryResponse.of(
                entry, page, entry.getGuestCookieId() == null ? null : guessesByGuest.get(entry.getGuestCookieId())))
            .toList();
    }
}

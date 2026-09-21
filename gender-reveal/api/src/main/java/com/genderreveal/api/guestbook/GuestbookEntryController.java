package com.genderreveal.api.guestbook;

import com.genderreveal.api.common.GuestCookie;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pages/{slug}/guestbook")
public class GuestbookEntryController {

    private final GuestbookEntryService guestbookEntryService;
    private final GuestCookie guestCookie;

    public GuestbookEntryController(GuestbookEntryService guestbookEntryService, GuestCookie guestCookie) {
        this.guestbookEntryService = guestbookEntryService;
        this.guestCookie = guestCookie;
    }

    @GetMapping
    public ResponseEntity<List<GuestbookEntryResponse>> list(@PathVariable String slug) {
        List<GuestbookEntryResponse> entries = guestbookEntryService.list(slug).stream()
            .map(GuestbookEntryResponse::from)
            .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entries);
    }

    @PostMapping
    public ResponseEntity<GuestbookEntryResponse> create(
            @PathVariable String slug,
            @CookieValue(name = GuestCookie.NAME, required = false) String existingGuestId,
            @Valid @RequestBody GuestbookEntryCreateRequest request) {
        String guestId = guestCookie.isValid(existingGuestId) ? existingGuestId : null;
        GuestbookEntry entry = guestbookEntryService.create(slug, request.nickname(), request.message(), guestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(GuestbookEntryResponse.from(entry));
    }
}

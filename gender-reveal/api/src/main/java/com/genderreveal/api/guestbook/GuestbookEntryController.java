package com.genderreveal.api.guestbook;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pages/{slug}/guestbook")
public class GuestbookEntryController {

    private final GuestbookEntryService guestbookEntryService;

    public GuestbookEntryController(GuestbookEntryService guestbookEntryService) {
        this.guestbookEntryService = guestbookEntryService;
    }

    @GetMapping
    public List<GuestbookEntryResponse> list(@PathVariable String slug) {
        return guestbookEntryService.list(slug).stream()
            .map(GuestbookEntryResponse::from)
            .toList();
    }
}

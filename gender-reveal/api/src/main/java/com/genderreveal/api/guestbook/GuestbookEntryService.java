package com.genderreveal.api.guestbook;

import com.genderreveal.api.common.RateLimitExceededException;
import com.genderreveal.api.common.RateLimiter;
import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageNotFoundException;
import com.genderreveal.api.page.PageNotOpenException;
import com.genderreveal.api.page.PageRepository;
import com.genderreveal.api.page.PageStatus;
import com.genderreveal.api.page.PageStatusCalculator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class GuestbookEntryService {

    private static final int MAX_ENTRIES_PER_MINUTE = 5;

    private final GuestbookEntryRepository guestbookEntryRepository;
    private final PageRepository pageRepository;
    private final PageStatusCalculator statusCalculator;
    private final Clock clock;
    private final RateLimiter rateLimiter;

    public GuestbookEntryService(GuestbookEntryRepository guestbookEntryRepository, PageRepository pageRepository,
                                  PageStatusCalculator statusCalculator, Clock clock, RateLimiter rateLimiter) {
        this.guestbookEntryRepository = guestbookEntryRepository;
        this.pageRepository = pageRepository;
        this.statusCalculator = statusCalculator;
        this.clock = clock;
        this.rateLimiter = rateLimiter;
    }

    public List<GuestbookEntry> list(String slug) {
        Page page = requireOpenPage(slug);
        return guestbookEntryRepository.findByPageIdAndHiddenFalseOrderByCreatedAtDesc(page.getId());
    }

    public GuestbookEntry create(String slug, String nickname, String message, String guestCookieId,
                                  String rateLimitKey) {
        if (!rateLimiter.allow("guestbook:" + rateLimitKey, MAX_ENTRIES_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new RateLimitExceededException("Too many messages — try again in a minute");
        }
        Page page = requireOpenPage(slug);
        GuestbookEntry entry = new GuestbookEntry(page.getId(), nickname, message, guestCookieId, Instant.now(clock));
        return guestbookEntryRepository.save(entry);
    }

    private Page requireOpenPage(String slug) {
        Page page = pageRepository.findBySlug(slug)
            .orElseThrow(() -> new PageNotFoundException(slug));

        PageStatus status = statusCalculator.calculate(page, Instant.now(clock));
        if (status != PageStatus.OPEN) {
            throw new PageNotOpenException(slug);
        }
        return page;
    }
}

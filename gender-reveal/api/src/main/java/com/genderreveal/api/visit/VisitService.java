package com.genderreveal.api.visit;

import com.genderreveal.api.page.PageNotFoundException;
import com.genderreveal.api.page.PageRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class VisitService {

    private final PageVisitRepository visitRepository;
    private final PageRepository pageRepository;
    private final Clock clock;

    public VisitService(PageVisitRepository visitRepository, PageRepository pageRepository, Clock clock) {
        this.visitRepository = visitRepository;
        this.pageRepository = pageRepository;
        this.clock = clock;
    }

    /** Records one visit per (page, guest). Idempotent; a lost insert race is treated as "already recorded". */
    public void record(String slug, String guestId) {
        Long pageId = pageRepository.findBySlug(slug)
            .orElseThrow(() -> new PageNotFoundException(slug)).getId();
        if (visitRepository.existsByPageIdAndGuestCookieId(pageId, guestId)) {
            return;
        }
        try {
            visitRepository.save(new PageVisit(pageId, guestId, Instant.now(clock)));
        } catch (DataAccessException ex) {
            // Another request recorded the same (page_id, guest_cookie_id) first; UNIQUE guarantees exactly one row.
        }
    }
}

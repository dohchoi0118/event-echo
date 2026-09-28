package com.genderreveal.api.owner;

import com.genderreveal.api.guess.GuessRepository;
import com.genderreveal.api.guestbook.GuestbookEntryRepository;
import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageNotFoundException;
import com.genderreveal.api.page.PageRepository;
import com.genderreveal.api.page.PageStatusCalculator;
import com.genderreveal.api.visit.PageVisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OwnerPageService {

    private static final int EXTENSION_DAYS = 30;

    private final PageRepository pageRepository;
    private final PageVisitRepository visitRepository;
    private final GuessRepository guessRepository;
    private final GuestbookEntryRepository guestbookEntryRepository;
    private final PageStatusCalculator statusCalculator;
    private final Clock clock;

    public OwnerPageService(PageRepository pageRepository, PageVisitRepository visitRepository,
                             GuessRepository guessRepository, GuestbookEntryRepository guestbookEntryRepository,
                             PageStatusCalculator statusCalculator, Clock clock) {
        this.pageRepository = pageRepository;
        this.visitRepository = visitRepository;
        this.guessRepository = guessRepository;
        this.guestbookEntryRepository = guestbookEntryRepository;
        this.statusCalculator = statusCalculator;
        this.clock = clock;
    }

    /** Missing and not-yours are indistinguishable on purpose (404, no existence leak). */
    public Page requireOwned(String slug, String ownerEmail) {
        return pageRepository.findBySlug(slug)
            .filter(page -> page.getOwnerEmail().equalsIgnoreCase(ownerEmail))
            .orElseThrow(() -> new PageNotFoundException(slug));
    }

    public List<OwnerPageSummary> list(String ownerEmail) {
        Instant now = Instant.now(clock);
        return pageRepository.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail).stream()
            .map(page -> OwnerPageSummary.of(page, statusCalculator.calculate(page, now)))
            .toList();
    }

    public PageStats stats(String slug, String ownerEmail) {
        Long pageId = requireOwned(slug, ownerEmail).getId();
        return new PageStats(
            visitRepository.countByPageId(pageId),
            guessRepository.countByPageId(pageId),
            guessRepository.countByPageIdAndGuessedGender(pageId, "boy"),
            guessRepository.countByPageIdAndGuessedGender(pageId, "girl"));
    }

    public OwnerPageSummary extend(String slug, String ownerEmail) {
        Page page = requireOwned(slug, ownerEmail);
        Instant now = Instant.now(clock);
        Instant base = page.getExpiresAt().isAfter(now) ? page.getExpiresAt() : now;
        Instant newExpiresAt = base.plus(EXTENSION_DAYS, ChronoUnit.DAYS);

        if (pageRepository.markExtended(page.getId(), newExpiresAt) == 0) {
            throw new ExtensionAlreadyUsedException(slug);
        }

        Page saved = pageRepository.findById(page.getId()).orElseThrow(() -> new PageNotFoundException(slug));
        return OwnerPageSummary.of(saved, statusCalculator.calculate(saved, now));
    }

    public OwnerPageDetail detail(String slug, String ownerEmail) {
        Page page = requireOwned(slug, ownerEmail);
        return OwnerPageDetail.of(page, statusCalculator.calculate(page, Instant.now(clock)));
    }

    /** Sets the actual gender separately from page creation (e.g. by the doctor), not by the
     *  page owner filling out the create form — see PageStatusCalculator for how this gates OPEN. */
    public OwnerPageDetail setActualGender(String slug, String ownerEmail, String actualGender) {
        Page page = requireOwned(slug, ownerEmail);
        page.setActualGender(actualGender);
        Page saved = pageRepository.save(page);
        return OwnerPageDetail.of(saved, statusCalculator.calculate(saved, Instant.now(clock)));
    }

    /** Permanent, irreversible delete of the page and all its data (guesses, guestbook entries,
     *  visit records) — distinct from the 30-day retention/expiry lifecycle, which never deletes
     *  data. An explicit owner action, not something that happens automatically. */
    @Transactional
    public void delete(String slug, String ownerEmail) {
        Page page = requireOwned(slug, ownerEmail);
        Long pageId = page.getId();
        guessRepository.deleteByPageId(pageId);
        guestbookEntryRepository.deleteByPageId(pageId);
        visitRepository.deleteByPageId(pageId);
        pageRepository.delete(page);
    }
}

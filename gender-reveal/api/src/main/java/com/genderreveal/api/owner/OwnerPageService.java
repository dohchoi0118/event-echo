package com.genderreveal.api.owner;

import com.genderreveal.api.guess.GuessRepository;
import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageNotFoundException;
import com.genderreveal.api.page.PageRepository;
import com.genderreveal.api.page.PageStatusCalculator;
import com.genderreveal.api.visit.PageVisitRepository;
import org.springframework.stereotype.Service;

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
    private final PageStatusCalculator statusCalculator;
    private final Clock clock;

    public OwnerPageService(PageRepository pageRepository, PageVisitRepository visitRepository,
                             GuessRepository guessRepository, PageStatusCalculator statusCalculator, Clock clock) {
        this.pageRepository = pageRepository;
        this.visitRepository = visitRepository;
        this.guessRepository = guessRepository;
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
        if (page.isExtended()) {
            throw new ExtensionAlreadyUsedException(slug);
        }
        Instant now = Instant.now(clock);
        Instant base = page.getExpiresAt().isAfter(now) ? page.getExpiresAt() : now;
        page.extend(base.plus(EXTENSION_DAYS, ChronoUnit.DAYS));
        Page saved = pageRepository.save(page);
        return OwnerPageSummary.of(saved, statusCalculator.calculate(saved, now));
    }
}

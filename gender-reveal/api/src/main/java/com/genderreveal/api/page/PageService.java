package com.genderreveal.api.page;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class PageService {

    private static final int RETENTION_DAYS = 30;

    private final PageRepository pageRepository;
    private final UniqueSlugAllocator slugAllocator;
    private final PageStatusCalculator statusCalculator;
    private final Clock clock;

    public PageService(PageRepository pageRepository, UniqueSlugAllocator slugAllocator,
                        PageStatusCalculator statusCalculator, Clock clock) {
        this.pageRepository = pageRepository;
        this.slugAllocator = slugAllocator;
        this.statusCalculator = statusCalculator;
        this.clock = clock;
    }

    public Page create(PageCreateRequest request) {
        Instant now = Instant.now(clock);
        validateRevealAt(request.revealAt(), now);

        String slug = resolveSlug(request.slug());

        Page page = new Page(
            slug, request.nickname(), request.actualGender(), request.revealAt(),
            request.dueDate(), request.message(), request.theme(), request.bgmEnabled(),
            request.ownerEmail(), now, now.plus(RETENTION_DAYS, ChronoUnit.DAYS)
        );

        try {
            return pageRepository.save(page);
        } catch (DataAccessException ex) {
            // TOCTOU backstop: another request took this slug between resolveSlug() and save().
            // Catches DataAccessException, not the narrower DataIntegrityViolationException, because
            // the SQLite dialect reports no SQLState, so the UNIQUE(slug) violation surfaces as
            // JpaSystemException (a DataAccessException but NOT a DataIntegrityViolationException).
            // This also covers other persistence failures on this single-row insert; all other
            // columns are validated upstream, so the slug constraint is the only realistic cause.
            // Revisit if that changes.
            throw new SlugAlreadyTakenException(slug);
        }
    }

    public PagePublicResponse getPublicView(String slug) {
        Page page = pageRepository.findBySlug(slug)
            .orElseThrow(() -> new PageNotFoundException(slug));

        PageStatus status = statusCalculator.calculate(page, Instant.now(clock));

        return switch (status) {
            case SECRET -> PagePublicResponse.secretOrExpired("secret", page.getNickname());
            case EXPIRED -> PagePublicResponse.secretOrExpired("expired", page.getNickname());
            case OPEN -> PagePublicResponse.open(page);
        };
    }

    private void validateRevealAt(Instant revealAt, Instant now) {
        Instant latestAllowedRevealAt = now.plus(RETENTION_DAYS, ChronoUnit.DAYS);
        if (!revealAt.isBefore(latestAllowedRevealAt)) {
            throw new InvalidRevealAtException(
                "revealAt must be strictly before " + RETENTION_DAYS + " days from now, otherwise the page "
                    + "would expire before it ever opens");
        }
    }

    private String resolveSlug(String requestedSlug) {
        if (requestedSlug == null || requestedSlug.isBlank()) {
            return slugAllocator.allocate();
        }
        if (pageRepository.existsBySlug(requestedSlug)) {
            throw new SlugAlreadyTakenException(requestedSlug);
        }
        return requestedSlug;
    }
}

package com.genderreveal.api.page;

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
        String slug = resolveSlug(request.slug());
        Instant now = Instant.now(clock);

        Page page = new Page(
            slug, request.nickname(), request.actualGender(), request.revealAt(),
            request.dueDate(), request.message(), request.theme(), request.bgmEnabled(),
            request.ownerEmail(), now, now.plus(RETENTION_DAYS, ChronoUnit.DAYS)
        );

        return pageRepository.save(page);
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

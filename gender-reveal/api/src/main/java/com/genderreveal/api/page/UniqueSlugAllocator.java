package com.genderreveal.api.page;

import org.springframework.stereotype.Component;

@Component
public class UniqueSlugAllocator {

    private static final int MAX_ATTEMPTS = 10;

    private final SlugGenerator slugGenerator;
    private final PageRepository pageRepository;

    public UniqueSlugAllocator(SlugGenerator slugGenerator, PageRepository pageRepository) {
        this.slugGenerator = slugGenerator;
        this.pageRepository = pageRepository;
    }

    public String allocate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = slugGenerator.generate();
            if (!pageRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to allocate a unique slug after " + MAX_ATTEMPTS + " attempts");
    }
}

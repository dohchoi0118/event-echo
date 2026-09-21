package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    private final SlugGenerator generator = new SlugGenerator();
    private static final Pattern VALID_SLUG = Pattern.compile("^[a-z0-9]{8}$");

    @Test
    void generatesEightCharacterLowercaseAlphanumericSlug() {
        String slug = generator.generate();

        assertThat(slug).matches(VALID_SLUG);
    }

    @Test
    void generatesDistinctSlugsAcrossManyCalls() {
        Set<String> slugs = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            slugs.add(generator.generate());
        }

        assertThat(slugs).hasSize(1000);
    }
}

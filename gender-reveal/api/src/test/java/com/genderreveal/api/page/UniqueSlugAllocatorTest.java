package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UniqueSlugAllocatorTest {

    @Mock
    private PageRepository pageRepository;

    @Test
    void returnsFirstCandidateWhenNotTaken() {
        SlugGenerator fixedGenerator = new SlugGenerator() {
            @Override
            public String generate() {
                return "freeslug1";
            }
        };
        when(pageRepository.existsBySlug("freeslug1")).thenReturn(false);

        UniqueSlugAllocator allocator = new UniqueSlugAllocator(fixedGenerator, pageRepository);

        assertThat(allocator.allocate()).isEqualTo("freeslug1");
    }

    @Test
    void throwsAfterExhaustingAttemptsWhenAlwaysTaken() {
        when(pageRepository.existsBySlug(anyString())).thenReturn(true);

        UniqueSlugAllocator allocator = new UniqueSlugAllocator(new SlugGenerator(), pageRepository);

        assertThatThrownBy(allocator::allocate).isInstanceOf(IllegalStateException.class);
    }
}

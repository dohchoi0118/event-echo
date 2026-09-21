package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PageExceptionHandlerTest {

    private final PageExceptionHandler handler = new PageExceptionHandler();

    @Test
    void mapsDataIntegrityViolationToConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("UNIQUE constraint failed: pages.slug");

        ResponseEntity<Map<String, String>> response = handler.handleSlugConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Slug already taken");
    }

    @Test
    void mapsIllegalStateExceptionToServerErrorWithJsonBody() {
        IllegalStateException ex = new IllegalStateException("Failed to allocate a unique slug after 10 attempts");

        ResponseEntity<Map<String, String>> response = handler.handleSlugAllocationExhausted(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Failed to allocate a unique slug after 10 attempts");
    }
}

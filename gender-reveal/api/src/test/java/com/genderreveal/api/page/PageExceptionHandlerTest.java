package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PageExceptionHandlerTest {

    private final PageExceptionHandler handler = new PageExceptionHandler();

    @Test
    void mapsSlugAllocationExhaustedToServerError() {
        SlugAllocationExhaustedException ex = new SlugAllocationExhaustedException("Failed to allocate a unique slug after 10 attempts");

        ResponseEntity<Map<String, String>> response = handler.handleSlugAllocationExhausted(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Failed to allocate a unique slug after 10 attempts");
    }

    @Test
    void mapsPageNotOpenToConflict() {
        PageNotOpenException ex = new PageNotOpenException("test-slug");

        ResponseEntity<Map<String, String>> response = handler.handlePageNotOpen(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Page is not open: test-slug");
    }
}

package com.genderreveal.api.guess;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/pages/{slug}/guesses")
public class GuessController {

    private static final String COOKIE_NAME = "guest_id";

    private final GuessService guessService;

    public GuessController(GuessService guessService) {
        this.guessService = guessService;
    }

    @PostMapping
    public ResponseEntity<GuessResponse> create(
            @PathVariable String slug,
            @CookieValue(name = COOKIE_NAME, required = false) String existingGuestId,
            @Valid @RequestBody GuessCreateRequest request) {

        String guestId = (existingGuestId != null && !existingGuestId.isBlank())
            ? existingGuestId
            : UUID.randomUUID().toString();

        Guess guess = guessService.create(slug, guestId, request.guessedGender());

        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, guestId)
            .path("/")
            .maxAge(Duration.ofDays(365))
            .httpOnly(true)
            .sameSite("Lax")
            .build();

        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(GuessResponse.from(guess));
    }
}

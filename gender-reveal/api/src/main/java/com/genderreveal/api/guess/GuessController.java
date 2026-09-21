package com.genderreveal.api.guess;

import com.genderreveal.api.common.GuestCookie;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pages/{slug}/guesses")
public class GuessController {

    private final GuessService guessService;
    private final GuestCookie guestCookie;

    public GuessController(GuessService guessService, GuestCookie guestCookie) {
        this.guessService = guessService;
        this.guestCookie = guestCookie;
    }

    @PostMapping
    public ResponseEntity<GuessResponse> create(
            @PathVariable String slug,
            @CookieValue(name = GuestCookie.NAME, required = false) String existingGuestId,
            @Valid @RequestBody GuessCreateRequest request) {

        String guestId = guestCookie.resolve(existingGuestId);

        Guess guess = guessService.create(slug, guestId, request.guessedGender());

        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, guestCookie.toSetCookie(guestId))
            .body(GuessResponse.from(guess));
    }
}

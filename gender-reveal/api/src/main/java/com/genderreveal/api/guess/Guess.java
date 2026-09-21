package com.genderreveal.api.guess;

import com.genderreveal.api.common.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "guesses")
public class Guess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(name = "guest_cookie_id", nullable = false)
    private String guestCookieId;

    @Column(name = "guessed_gender", nullable = false)
    private String guessedGender;

    @Column(name = "created_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant createdAt;

    protected Guess() {
        // JPA
    }

    public Guess(Long pageId, String guestCookieId, String guessedGender, Instant createdAt) {
        this.pageId = pageId;
        this.guestCookieId = guestCookieId;
        this.guessedGender = guessedGender;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getPageId() { return pageId; }
    public String getGuestCookieId() { return guestCookieId; }
    public String getGuessedGender() { return guessedGender; }
    public Instant getCreatedAt() { return createdAt; }
}

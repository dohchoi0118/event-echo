package com.genderreveal.api.page;

import com.genderreveal.api.common.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "actual_gender", nullable = false)
    private String actualGender;

    @Column(name = "reveal_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant revealAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    private String message;

    @Column(nullable = false)
    private String theme;

    @Column(name = "bgm_enabled", nullable = false)
    private boolean bgmEnabled;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "created_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean extended;

    protected Page() {
        // JPA
    }

    public Page(String slug, String nickname, String actualGender, Instant revealAt,
                LocalDate dueDate, String message, String theme, boolean bgmEnabled,
                String ownerEmail, Instant createdAt, Instant expiresAt) {
        this.slug = slug;
        this.nickname = nickname;
        this.actualGender = actualGender;
        this.revealAt = revealAt;
        this.dueDate = dueDate;
        this.message = message;
        this.theme = theme;
        this.bgmEnabled = bgmEnabled;
        this.ownerEmail = ownerEmail;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.extended = false;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getNickname() { return nickname; }
    public String getActualGender() { return actualGender; }
    public Instant getRevealAt() { return revealAt; }
    public LocalDate getDueDate() { return dueDate; }
    public String getMessage() { return message; }
    public String getTheme() { return theme; }
    public boolean isBgmEnabled() { return bgmEnabled; }
    public String getOwnerEmail() { return ownerEmail; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExtended() { return extended; }
}

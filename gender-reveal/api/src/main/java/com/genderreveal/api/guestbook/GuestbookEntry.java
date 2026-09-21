package com.genderreveal.api.guestbook;

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
@Table(name = "guestbook_entries")
public class GuestbookEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant createdAt;

    @Column(name = "guest_cookie_id")
    private String guestCookieId;

    protected GuestbookEntry() {
        // JPA
    }

    public GuestbookEntry(Long pageId, String nickname, String message, Instant createdAt) {
        this(pageId, nickname, message, null, createdAt);
    }

    public GuestbookEntry(Long pageId, String nickname, String message, String guestCookieId, Instant createdAt) {
        this.pageId = pageId;
        this.nickname = nickname;
        this.message = message;
        this.guestCookieId = guestCookieId;
        this.hidden = false;
        this.createdAt = createdAt;
    }

    public void hide() {
        this.hidden = true;
    }

    public Long getId() { return id; }
    public Long getPageId() { return pageId; }
    public String getNickname() { return nickname; }
    public String getMessage() { return message; }
    public boolean isHidden() { return hidden; }
    public Instant getCreatedAt() { return createdAt; }
    public String getGuestCookieId() { return guestCookieId; }
}

package com.genderreveal.api.visit;

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
@Table(name = "page_visits")
public class PageVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(name = "guest_cookie_id", nullable = false)
    private String guestCookieId;

    @Column(name = "created_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant createdAt;

    protected PageVisit() {
        // JPA
    }

    public PageVisit(Long pageId, String guestCookieId, Instant createdAt) {
        this.pageId = pageId;
        this.guestCookieId = guestCookieId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getPageId() { return pageId; }
    public String getGuestCookieId() { return guestCookieId; }
    public Instant getCreatedAt() { return createdAt; }
}

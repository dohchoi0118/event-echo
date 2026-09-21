package com.genderreveal.api.guestbook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuestbookEntryRepository extends JpaRepository<GuestbookEntry, Long> {
    List<GuestbookEntry> findByPageIdAndHiddenFalseOrderByCreatedAtDesc(Long pageId);
}

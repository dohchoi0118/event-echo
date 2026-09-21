package com.genderreveal.api.visit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PageVisitRepository extends JpaRepository<PageVisit, Long> {
    boolean existsByPageIdAndGuestCookieId(Long pageId, String guestCookieId);

    long countByPageId(Long pageId);
}

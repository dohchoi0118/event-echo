package com.genderreveal.api.owner;

import com.genderreveal.api.auth.OwnerTestSupport;
import com.genderreveal.api.guess.Guess;
import com.genderreveal.api.guess.GuessRepository;
import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageRepository;
import com.genderreveal.api.visit.PageVisit;
import com.genderreveal.api.visit.PageVisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnerPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OwnerTestSupport ownerTestSupport;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private GuessRepository guessRepository;

    @Autowired
    private PageVisitRepository visitRepository;

    @Test
    void listRequiresSession() throws Exception {
        mockMvc.perform(get("/api/owner/pages")).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsOnlyOwnPagesNewestFirstWithStatus() throws Exception {
        savePage("list-mine-old", "list-owner@example.com", -1, 0);
        savePage("list-mine-new", "list-owner@example.com", 24, 1);
        savePage("list-theirs", "someone-else@example.com", -1, 2);

        mockMvc.perform(get("/api/owner/pages").cookie(ownerTestSupport.cookieFor("list-owner@example.com")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].slug").value("list-mine-new"))
            .andExpect(jsonPath("$[0].status").value("secret"))
            .andExpect(jsonPath("$[1].slug").value("list-mine-old"))
            .andExpect(jsonPath("$[1].status").value("open"))
            .andExpect(jsonPath("$[1].extended").value(false));
    }

    @Test
    void statsCountVisitorsAndGuessesByGender() throws Exception {
        Page page1 = savePage("stats-slug", "stats-owner@example.com", -1, 0);
        Page page2 = savePage("stats-other-slug", "stats-owner@example.com", -1, 1);
        Instant now = Instant.now();

        // Page 1: 4 visitors, 3 guessers (v4 visits but never guesses)
        visitRepository.save(new PageVisit(page1.getId(), "v1", now));
        visitRepository.save(new PageVisit(page1.getId(), "v2", now));
        visitRepository.save(new PageVisit(page1.getId(), "v3", now));
        visitRepository.save(new PageVisit(page1.getId(), "v4", now));
        guessRepository.save(new Guess(page1.getId(), "v1", "boy", now));
        guessRepository.save(new Guess(page1.getId(), "v2", "boy", now));
        guessRepository.save(new Guess(page1.getId(), "v3", "girl", now));

        // Page 2: 2 visitors, 1 guess (must not be counted in page1 stats)
        visitRepository.save(new PageVisit(page2.getId(), "v5", now));
        visitRepository.save(new PageVisit(page2.getId(), "v6", now));
        guessRepository.save(new Guess(page2.getId(), "v5", "boy", now));

        mockMvc.perform(get("/api/owner/pages/stats-slug/stats")
                .cookie(ownerTestSupport.cookieFor("stats-owner@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visitors").value(4))
            .andExpect(jsonPath("$.guessers").value(3))
            .andExpect(jsonPath("$.boyGuesses").value(2))
            .andExpect(jsonPath("$.girlGuesses").value(1));
    }

    @Test
    void statsOfSomeoneElsesPageLooksLikeNotFound() throws Exception {
        savePage("stats-private-slug", "victim@example.com", -1, 0);

        mockMvc.perform(get("/api/owner/pages/stats-private-slug/stats")
                .cookie(ownerTestSupport.cookieFor("intruder@example.com")))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/owner/pages/no-such-page/stats")
                .cookie(ownerTestSupport.cookieFor("intruder@example.com")))
            .andExpect(status().isNotFound());
    }

    @Test
    void extendAddsThirtyDaysToTheCurrentExpiryOnceOnly() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page page = pageRepository.save(new Page(
            "ext-slug", "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "ext-owner@example.com", now, now.plus(5, ChronoUnit.DAYS)));

        mockMvc.perform(post("/api/owner/pages/ext-slug/extend")
                .cookie(ownerTestSupport.cookieFor("ext-owner@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.extended").value(true))
            .andExpect(jsonPath("$.status").value("open"));

        Page reloaded = pageRepository.findById(page.getId()).orElseThrow();
        assertThat(reloaded.isExtended()).isTrue();
        assertThat(reloaded.getExpiresAt()).isEqualTo(now.plus(35, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/owner/pages/ext-slug/extend")
                .cookie(ownerTestSupport.cookieFor("ext-owner@example.com")))
            .andExpect(status().isConflict());
    }

    @Test
    void extendingAnAlreadyExpiredPageRestartsThirtyDaysFromNow() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page page = pageRepository.save(new Page(
            "ext-expired-slug", "뽀튼이", "boy", now.minus(40, ChronoUnit.DAYS),
            null, "메시지", "box", false, "ext-owner@example.com",
            now.minus(35, ChronoUnit.DAYS), now.minus(5, ChronoUnit.DAYS)));

        mockMvc.perform(post("/api/owner/pages/ext-expired-slug/extend")
                .cookie(ownerTestSupport.cookieFor("ext-owner@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("open"));

        Page reloaded = pageRepository.findById(page.getId()).orElseThrow();
        assertThat(reloaded.getExpiresAt()).isCloseTo(now.plus(30, ChronoUnit.DAYS), within(30, ChronoUnit.SECONDS));
    }

    @Test
    void concurrentExtendRequestsOnlyOneSucceeds() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        pageRepository.save(new Page(
            "concurrent-extend-slug", "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "concurrent-owner@example.com", now, now.plus(5, ChronoUnit.DAYS)));
        var cookie = ownerTestSupport.cookieFor("concurrent-owner@example.com");

        mockMvc.perform(post("/api/owner/pages/concurrent-extend-slug/extend").cookie(cookie))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/owner/pages/concurrent-extend-slug/extend").cookie(cookie))
            .andExpect(status().isConflict());
    }

    @Test
    void extendingSomeoneElsesPageIs404() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        pageRepository.save(new Page(
            "ext-private-slug", "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "victim@example.com", now, now.plus(5, ChronoUnit.DAYS)));

        mockMvc.perform(post("/api/owner/pages/ext-private-slug/extend")
                .cookie(ownerTestSupport.cookieFor("intruder@example.com")))
            .andExpect(status().isNotFound());
    }

    @Test
    void detailExposesActualGenderAndSettingsToTheOwner() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        pageRepository.save(new Page(
            "detail-slug", "뽀튼이", "girl", now.plus(2, ChronoUnit.HOURS),
            LocalDate.of(2026, 11, 3), "환영해요", "cake", false,
            "detail-owner@example.com", now, now.plus(30, ChronoUnit.DAYS)));

        mockMvc.perform(get("/api/owner/pages/detail-slug")
                .cookie(ownerTestSupport.cookieFor("detail-owner@example.com")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.slug").value("detail-slug"))
            .andExpect(jsonPath("$.nickname").value("뽀튼이"))
            .andExpect(jsonPath("$.actualGender").value("girl"))
            .andExpect(jsonPath("$.dueDate").value("2026-11-03"))
            .andExpect(jsonPath("$.message").value("환영해요"))
            .andExpect(jsonPath("$.theme").value("cake"))
            .andExpect(jsonPath("$.bgmEnabled").value(false))
            .andExpect(jsonPath("$.status").value("secret"))
            .andExpect(jsonPath("$.extended").value(false));
    }

    @Test
    void detailOfSomeoneElsesPageOrMissingSlugIs404() throws Exception {
        pageRepository.save(new Page(
            "detail-private-slug", "뽀튼이", "boy", Instant.now().minus(1, ChronoUnit.HOURS),
            null, null, "box", false, "victim@example.com",
            Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS)));

        mockMvc.perform(get("/api/owner/pages/detail-private-slug")
                .cookie(ownerTestSupport.cookieFor("intruder@example.com")))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/owner/pages/no-such-slug")
                .cookie(ownerTestSupport.cookieFor("intruder@example.com")))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/owner/pages/detail-private-slug"))
            .andExpect(status().isUnauthorized());
    }

    private Page savePage(String slug, String ownerEmail, long revealOffsetHours, long createdOffsetMinutes) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return pageRepository.save(new Page(
            slug, "뽀튼이", "boy", now.plus(revealOffsetHours, ChronoUnit.HOURS),
            null, "메시지", "box", false, ownerEmail,
            now.plus(createdOffsetMinutes, ChronoUnit.MINUTES), now.plus(30, ChronoUnit.DAYS)));
    }
}

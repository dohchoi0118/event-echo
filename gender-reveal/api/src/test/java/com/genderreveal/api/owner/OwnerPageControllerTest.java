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
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        Page page = savePage("stats-slug", "stats-owner@example.com", -1, 0);
        Instant now = Instant.now();
        visitRepository.save(new PageVisit(page.getId(), "v1", now));
        visitRepository.save(new PageVisit(page.getId(), "v2", now));
        visitRepository.save(new PageVisit(page.getId(), "v3", now));
        guessRepository.save(new Guess(page.getId(), "v1", "boy", now));
        guessRepository.save(new Guess(page.getId(), "v2", "boy", now));
        guessRepository.save(new Guess(page.getId(), "v3", "girl", now));

        mockMvc.perform(get("/api/owner/pages/stats-slug/stats")
                .cookie(ownerTestSupport.cookieFor("stats-owner@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visitors").value(3))
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

    private Page savePage(String slug, String ownerEmail, long revealOffsetHours, long createdOffsetMinutes) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return pageRepository.save(new Page(
            slug, "뽀튼이", "boy", now.plus(revealOffsetHours, ChronoUnit.HOURS),
            null, "메시지", "box", false, ownerEmail,
            now.plus(createdOffsetMinutes, ChronoUnit.MINUTES), now.plus(30, ChronoUnit.DAYS)));
    }
}

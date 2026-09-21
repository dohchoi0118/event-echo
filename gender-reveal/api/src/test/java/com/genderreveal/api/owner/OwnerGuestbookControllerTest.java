package com.genderreveal.api.owner;

import com.genderreveal.api.auth.OwnerTestSupport;
import com.genderreveal.api.guess.Guess;
import com.genderreveal.api.guess.GuessRepository;
import com.genderreveal.api.guestbook.GuestbookEntry;
import com.genderreveal.api.guestbook.GuestbookEntryRepository;
import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnerGuestbookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OwnerTestSupport ownerTestSupport;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private GuestbookEntryRepository guestbookEntryRepository;

    @Autowired
    private GuessRepository guessRepository;

    @Test
    void listsAllEntriesWithGuessResultsIncludingHiddenAndOnASecretPage() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        // Secret page (reveal in the future): owner can still moderate; actualGender is boy.
        Page page = pageRepository.save(new Page(
            "og-slug", "뽀튼이", "boy", now.plus(2, ChronoUnit.HOURS),
            null, "메시지", "box", false, "og-owner@example.com", now, now.plus(30, ChronoUnit.DAYS)));

        guessRepository.save(new Guess(page.getId(), "c-right", "boy", now));
        guessRepository.save(new Guess(page.getId(), "c-wrong", "girl", now));

        guestbookEntryRepository.save(new GuestbookEntry(page.getId(), "정답이", "1", "c-right", now.plusSeconds(1)));
        guestbookEntryRepository.save(new GuestbookEntry(page.getId(), "오답이", "2", "c-wrong", now.plusSeconds(2)));
        guestbookEntryRepository.save(new GuestbookEntry(page.getId(), "쿠키없음", "3", now.plusSeconds(3)));
        guestbookEntryRepository.save(new GuestbookEntry(page.getId(), "안맞춤", "4", "c-no-guess", now.plusSeconds(4)));
        GuestbookEntry hidden = guestbookEntryRepository.save(
            new GuestbookEntry(page.getId(), "숨김", "5", "c-right", now.plusSeconds(5)));
        hidden.hide();
        guestbookEntryRepository.save(hidden);

        mockMvc.perform(get("/api/owner/pages/og-slug/guestbook")
                .cookie(ownerTestSupport.cookieFor("og-owner@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[0].nickname").value("숨김"))
            .andExpect(jsonPath("$[0].hidden").value(true))
            .andExpect(jsonPath("$[1].nickname").value("안맞춤"))
            .andExpect(jsonPath("$[1].guessedGender").doesNotHaveJsonPath())
            .andExpect(jsonPath("$[1].guessCorrect").doesNotHaveJsonPath())
            .andExpect(jsonPath("$[2].nickname").value("쿠키없음"))
            .andExpect(jsonPath("$[2].guessedGender").doesNotHaveJsonPath())
            .andExpect(jsonPath("$[3].nickname").value("오답이"))
            .andExpect(jsonPath("$[3].guessedGender").value("girl"))
            .andExpect(jsonPath("$[3].guessCorrect").value(false))
            .andExpect(jsonPath("$[4].nickname").value("정답이"))
            .andExpect(jsonPath("$[4].guessedGender").value("boy"))
            .andExpect(jsonPath("$[4].guessCorrect").value(true));
    }

    @Test
    void requiresSessionAndOwnership() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        pageRepository.save(new Page(
            "og-private", "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "victim@example.com", now, now.plus(30, ChronoUnit.DAYS)));

        mockMvc.perform(get("/api/owner/pages/og-private/guestbook")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/owner/pages/og-private/guestbook")
                .cookie(ownerTestSupport.cookieFor("intruder@example.com")))
            .andExpect(status().isNotFound());
    }
}

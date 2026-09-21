package com.genderreveal.api.visit;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PageVisitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private PageVisitRepository visitRepository;

    @Test
    void firstOpenVisitIssuesCookieAndRecordsOneVisit() throws Exception {
        Page page = savePage("visit-open-slug", -1);

        MvcResult result = mockMvc.perform(get("/api/pages/visit-open-slug"))
            .andExpect(status().isOk())
            .andReturn();

        String guestId = result.getResponse().getCookie("guest_id").getValue();
        assertThat(UUID.fromString(guestId)).isNotNull();
        assertThat(visitRepository.countByPageId(page.getId())).isEqualTo(1);
    }

    @Test
    void repeatVisitWithSameCookieIsNotCountedTwice() throws Exception {
        Page page = savePage("visit-repeat-slug", -1);
        MockCookie cookie = new MockCookie("guest_id", UUID.randomUUID().toString());

        mockMvc.perform(get("/api/pages/visit-repeat-slug").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/pages/visit-repeat-slug").cookie(cookie)).andExpect(status().isOk());

        assertThat(visitRepository.countByPageId(page.getId())).isEqualTo(1);
    }

    @Test
    void differentGuestsAreCountedSeparately() throws Exception {
        Page page = savePage("visit-two-slug", -1);

        mockMvc.perform(get("/api/pages/visit-two-slug")
            .cookie(new MockCookie("guest_id", UUID.randomUUID().toString()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/pages/visit-two-slug")
            .cookie(new MockCookie("guest_id", UUID.randomUUID().toString()))).andExpect(status().isOk());

        assertThat(visitRepository.countByPageId(page.getId())).isEqualTo(2);
    }

    @Test
    void secretPageIssuesNoCookieAndRecordsNothing() throws Exception {
        Page page = savePage("visit-secret-slug", 24);

        MvcResult result = mockMvc.perform(get("/api/pages/visit-secret-slug"))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getResponse().getCookie("guest_id")).isNull();
        assertThat(visitRepository.countByPageId(page.getId())).isZero();
    }

    private Page savePage(String slug, long revealOffsetHours) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return pageRepository.save(new Page(
            slug, "뽀튼이", "boy", now.plus(revealOffsetHours, ChronoUnit.HOURS),
            null, "메시지", "box", false, "owner@example.com", now, now.plus(30, ChronoUnit.DAYS)));
    }
}

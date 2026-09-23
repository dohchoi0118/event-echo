package com.genderreveal.api.guestbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.auth.OwnerTestSupport;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GuestbookEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerTestSupport ownerTestSupport;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private GuestbookEntryRepository guestbookEntryRepository;

    @Test
    void hiddenEntriesAreExcludedFromList() throws Exception {
        String slug = createOpenPage("guestbook-hidden-slug");
        Page page = pageRepository.findBySlug(slug).orElseThrow();

        guestbookEntryRepository.save(new GuestbookEntry(page.getId(), "visible", "보여요", Instant.now()));
        GuestbookEntry hidden = guestbookEntryRepository.save(
            new GuestbookEntry(page.getId(), "hidden", "숨겨요", Instant.now()));
        hidden.hide();
        guestbookEntryRepository.save(hidden);

        mockMvc.perform(get("/api/pages/" + slug + "/guestbook"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].nickname").value("visible"));
    }

    @Test
    void setsNoStoreCacheControlHeader() throws Exception {
        String slug = createOpenPage("guestbook-cache-header-slug");

        mockMvc.perform(get("/api/pages/" + slug + "/guestbook"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void listOnUnknownSlugReturns404() throws Exception {
        mockMvc.perform(get("/api/pages/does-not-exist/guestbook"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createsEntryAndListsItNewestFirst() throws Exception {
        String slug = createOpenPage("guestbook-create-slug");

        mockMvc.perform(post("/api/pages/" + slug + "/guestbook")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("nickname", "이모", "message", "축하해요"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nickname").value("이모"));

        mockMvc.perform(post("/api/pages/" + slug + "/guestbook")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("nickname", "삼촌", "message", "고생하셨어요"))))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/pages/" + slug + "/guestbook"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].nickname").value("삼촌"))
            .andExpect(jsonPath("$[1].nickname").value("이모"));
    }

    @Test
    void postOnSecretPageIsRejected() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "slug", "guestbook-secret-slug"
        );
        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pages/guestbook-secret-slug/guestbook")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("nickname", "이모", "message", "축하해요"))))
            .andExpect(status().isConflict());
    }

    @Test
    void rejectsBlankNickname() throws Exception {
        String slug = createOpenPage("guestbook-blank-slug");

        mockMvc.perform(post("/api/pages/" + slug + "/guestbook")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("nickname", "", "message", "축하해요"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void storesGuestCookieIdWhenValidCookieIsSent() throws Exception {
        String slug = createOpenPage("guestbook-cookie-slug");
        Page page = pageRepository.findBySlug(slug).orElseThrow();
        String guestId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/pages/" + slug + "/guestbook")
                .cookie(new MockCookie("guest_id", guestId))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("nickname", "이모", "message", "축하해요"))))
            .andExpect(status().isCreated());

        assertThat(guestbookEntryRepository.findAll())
            .filteredOn(e -> e.getPageId().equals(page.getId()))
            .singleElement()
            .extracting(GuestbookEntry::getGuestCookieId).isEqualTo(guestId);
    }

    @Test
    void storesNullWhenCookieIsMissingOrNotAUuid() throws Exception {
        String slug = createOpenPage("guestbook-nocookie-slug");
        Page page = pageRepository.findBySlug(slug).orElseThrow();

        mockMvc.perform(post("/api/pages/" + slug + "/guestbook")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("nickname", "이모", "message", "쿠키 없음"))))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/pages/" + slug + "/guestbook")
                .cookie(new MockCookie("guest_id", "not-a-uuid"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("nickname", "삼촌", "message", "쿠키 이상"))))
            .andExpect(status().isCreated());

        assertThat(guestbookEntryRepository.findAll())
            .filteredOn(e -> e.getPageId().equals(page.getId()))
            .extracting(GuestbookEntry::getGuestCookieId)
            .containsOnlyNulls();
    }

    @Test
    void firstPostWithNoCookieIssuesGuestCookie() throws Exception {
        String slug = createOpenPage("guestbook-issues-cookie-slug");

        MvcResult result = mockMvc.perform(post("/api/pages/" + slug + "/guestbook")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("nickname", "이모", "message", "축하해요"))))
            .andExpect(status().isCreated())
            .andExpect(cookie().exists("guest_id"))
            .andReturn();

        String guestId = result.getResponse().getCookie("guest_id").getValue();
        assertThat(guestId).isNotBlank();
        assertThat(UUID.fromString(guestId)).isNotNull();
    }

    @Test
    void sixthGuestbookPostFromTheSameGuestWithinAMinuteIsRateLimited() throws Exception {
        String slug = createOpenPage("rate-limit-guestbook-slug");
        MockCookie cookie = new MockCookie("guest_id", UUID.randomUUID().toString());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/pages/" + slug + "/guestbook")
                    .cookie(cookie).contentType("application/json")
                    .content(objectMapper.writeValueAsString(Map.of("nickname", "이모", "message", "축하 " + i))))
                .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/pages/" + slug + "/guestbook")
                .cookie(cookie).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("nickname", "이모", "message", "또"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
    }

    private String createOpenPage(String slug) throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "slug", slug
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        return slug;
    }
}

package com.genderreveal.api.guestbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.auth.OwnerTestSupport;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

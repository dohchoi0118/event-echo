package com.genderreveal.api.guestbook;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    void listOnUnknownSlugReturns404() throws Exception {
        mockMvc.perform(get("/api/pages/does-not-exist/guestbook"))
            .andExpect(status().isNotFound());
    }

    private String createOpenPage(String slug) throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "ownerEmail", "owner@example.com",
            "slug", slug
        );

        mockMvc.perform(post("/api/pages")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        return slug;
    }
}

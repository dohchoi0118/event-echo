package com.genderreveal.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OwnerTestSupport ownerTestSupport;

    @Test
    void meReturnsSessionEmail() throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(ownerTestSupport.cookieFor("Me@Example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    void meWithoutSessionIs401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void meWithUnknownSessionTokenIs401() throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(new MockCookie(OwnerSessionCookie.NAME, "forged")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDeletesSessionAndClearsCookie() throws Exception {
        MockCookie session = ownerTestSupport.cookieFor("bye@example.com");

        mockMvc.perform(post("/api/auth/logout").cookie(session))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge(OwnerSessionCookie.NAME, 0));

        mockMvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pages/does-not-exist")).andExpect(status().isNotFound());
    }
}

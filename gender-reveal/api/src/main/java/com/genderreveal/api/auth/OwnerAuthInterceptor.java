package com.genderreveal.api.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class OwnerAuthInterceptor implements HandlerInterceptor {

    private final OwnerSessionService sessionService;

    public OwnerAuthInterceptor(OwnerSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // "/api/pages" is registered for the interceptor so POST (create) is guarded; GET-by-slug lives under "/api/pages/**" and is not matched.
        if ("/api/pages".equals(request.getRequestURI()) && !"POST".equals(request.getMethod())) {
            return true;
        }

        Optional<OwnerSession> session = sessionService.findValid(sessionCookieValue(request));
        if (session.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return false;
        }

        request.setAttribute(OwnerPrincipal.REQUEST_ATTRIBUTE, new OwnerPrincipal(session.get().getOwnerEmail()));
        return true;
    }

    private static String sessionCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (OwnerSessionCookie.NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

package com.genderreveal.api.auth;

import com.genderreveal.api.auth.magiclink.MagicLinkRequest;
import com.genderreveal.api.auth.magiclink.MagicLinkService;
import com.genderreveal.api.auth.session.OwnerPrincipal;
import com.genderreveal.api.auth.session.OwnerSessionCookie;
import com.genderreveal.api.auth.session.OwnerSessionService;
import com.genderreveal.api.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final MagicLinkService magicLinkService;
    private final OwnerSessionService sessionService;
    private final AppProperties appProperties;

    public AuthController(MagicLinkService magicLinkService, OwnerSessionService sessionService,
                           AppProperties appProperties) {
        this.magicLinkService = magicLinkService;
        this.sessionService = sessionService;
        this.appProperties = appProperties;
    }

    @PostMapping("/magic-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestMagicLink(@Valid @RequestBody MagicLinkRequest request, HttpServletRequest httpRequest) {
        magicLinkService.request(request.email(), clientIp(httpRequest));
    }

    /** Prefers the proxy-set header (nginx forwards it) over the raw socket address. */
    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("token") String token) {
        Optional<String> email = magicLinkService.consume(token);
        if (email.isEmpty()) {
            return redirect("/login?error=invalid").build();
        }
        String sessionToken = sessionService.create(email.get());
        return redirect("/dashboard")
            .header(HttpHeaders.SET_COOKIE, OwnerSessionCookie.issue(sessionToken, appProperties.sessionCookieSecure()))
            .build();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(OwnerPrincipal owner) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("email", owner.email()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = OwnerSessionCookie.NAME, required = false) String sessionToken) {
        sessionService.delete(sessionToken);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, OwnerSessionCookie.clear(appProperties.sessionCookieSecure()))
            .build();
    }

    private ResponseEntity.BodyBuilder redirect(String path) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(appProperties.baseUrl() + path))
            .cacheControl(CacheControl.noStore());
    }
}

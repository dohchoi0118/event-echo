# 젠더리빌 하드닝 계획 (Plan 6)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `gender-reveal/CLAUDE.md`의 "Known deferred hardening" 목록에 쌓인 항목 중, 사용자와 합의한 범위(레이트리밋, 검증 오류 형식 통일, 정리 스케줄러, 접근성 타이머, "이미 참여함" 표시, 잔여 견고화)를 처리한다.

**Architecture:** 새 인프라(Redis 등)를 들이지 않는다. 레이트리밋은 단일 인스턴스 in-memory 카운터로 충분하다(이 서비스 규모에 맞춤). 검증 오류는 기존 도메인 예외 핸들러와 같은 `{"error": "..."}` 형태로 한 곳(`common` 패키지)에서 통일한다. 정리 스케줄러는 Spring의 `@Scheduled`를 쓴다.

**Tech Stack:** Plan 1–5와 동일 (Spring Boot 3.3, Java 21 / Next.js 15, TypeScript). 새 의존성 없음.

**Spec:** [docs/superpowers/specs/2026-09-18-implementation-design.md](../specs/2026-09-18-implementation-design.md) 9절의 보류 목록, [gender-reveal/CLAUDE.md](../../../CLAUDE.md)의 "Known deferred hardening" 두 항목 목록이 이 계획의 근거다. 새로운 스펙 절은 만들지 않는다 — 완료 후 CLAUDE.md의 목록에서 처리된 항목을 지운다(Task 6 마지막 단계).

## 사용자와 합의한 범위 (이번에 하는 것 / 미루는 것)

- **레이트리밋은 게스트 쿠키 기준**(IP 아님 — 카페 와이파이 같은 공유 IP 오탐 방지). 맞추기·방명록 작성 각각 **분당 5회**, 초과 시 429 + `Retry-After: 60`.
- **쿠키 `Secure` 플래그는 이번에 안 켠다** — TLS/nginx를 붙이는 Docker 태스크 때 `app.session-cookie-secure`/게스트 쿠키를 함께 켠다. 이 계획은 그 설정을 건드리지 않는다.
- **"맞추기 전에도 개발자도구로 정답이 보이는" 문제는 계속 보류.** 별도 API 설계가 필요해 이 계획의 범위 밖.
- **방명록 페이지네이션은 이번에 제외.**
- **인트로 자동전환 타이머는 태명 길이에 비례해 연장**(접근성).
- **"이미 참여함"은 결과 화면에 안내 문구로 노출한다.**
- **검증 오류 JSON 형식은 기존 도메인 예외와 같은 `{"error": "..."}`로 통일.**

## Global Constraints

- 백엔드 작업(Task 1–3)은 `gender-reveal/api`에서만, 프론트 작업(Task 4–6)은 `gender-reveal/web`에서만 한다.
- 시간은 항상 주입된 `Clock`. 도메인 예외는 전용 타입 + 그 타입만 잡는 `@RestControllerAdvice`(제너릭 catch-all 금지 — 기존 규칙 유지).
- 테스트는 TDD. 전체 스위트(`./gradlew test`, `npm test`)가 항상 통과해야 한다.

---

### Task 1: 백엔드 — 게스트 쿠키 기준 레이트리밋 (맞추기, 방명록 작성)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/common/RateLimiter.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/common/RateLimitExceededException.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/common/RateLimitExceptionHandler.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessController.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryController.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryService.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/common/RateLimiterTest.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/guess/GuessControllerTest.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java`

**Interfaces:**
- Produces: `RateLimiter.allow(String key, int maxHits, Duration window): boolean`(true=허용+기록, false=거부), `RateLimitExceededException`, 429 + `Retry-After: 60` + `{"error": "..."}`; `GuessService.create`/`GuestbookEntryService.create`가 각각 `guestCookieId` 키로 분당 5회 제한

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/common/RateLimiterTest.java`:
```java
package com.genderreveal.api.common;

import com.genderreveal.api.config.MutableTestClock;
import com.genderreveal.api.config.MutableTestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
class RateLimiterTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private Clock clock;

    @Test
    void allowsUpToTheLimitThenRejects() {
        String key = "guest-" + System.nanoTime();

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.allow(key, 5, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(rateLimiter.allow(key, 5, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void differentKeysAreIndependent() {
        String a = "guest-a-" + System.nanoTime();
        String b = "guest-b-" + System.nanoTime();

        for (int i = 0; i < 5; i++) {
            rateLimiter.allow(a, 5, Duration.ofMinutes(1));
        }

        assertThat(rateLimiter.allow(a, 5, Duration.ofMinutes(1))).isFalse();
        assertThat(rateLimiter.allow(b, 5, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void windowSlidesForwardAsTimePasses() {
        String key = "guest-slide-" + System.nanoTime();
        MutableTestClock testClock = (MutableTestClock) clock;

        for (int i = 0; i < 5; i++) {
            rateLimiter.allow(key, 5, Duration.ofMinutes(1));
        }
        assertThat(rateLimiter.allow(key, 5, Duration.ofMinutes(1))).isFalse();

        testClock.advanceTo(testClock.instant().plus(61, ChronoUnit.SECONDS));

        assertThat(rateLimiter.allow(key, 5, Duration.ofMinutes(1))).isTrue();
    }
}
```

`GuessControllerTest.java`에 추가 (기존 헬퍼로 열린 페이지를 만들고 게스트 쿠키로 6번째 요청이 429인지 확인 — 클래스에 이미 있는 헬퍼/필드 이름에 맞춰 작성; `import java.util.UUID;`가 없으면 추가):
```java
    @Test
    void sixthGuessRequestFromTheSameGuestWithinAMinuteIsRateLimited() throws Exception {
        String slug = createOpenPage("rate-limit-guess-slug");
        MockCookie cookie = new MockCookie("guest_id", UUID.randomUUID().toString());
        // First guess succeeds (201); the other 4 hit DuplicateGuess (409) but each still counts
        // toward the limit, since the rate check runs before the duplicate check.
        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .cookie(cookie).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isCreated());
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                    .cookie(cookie).contentType("application/json")
                    .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
                .andExpect(status().isConflict());
        }

        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .cookie(cookie).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
    }
```

`GuestbookEntryControllerTest.java`에 추가 (`import org.springframework.mock.web.MockCookie;`, `import java.util.UUID;`가 없으면 추가):
```java
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
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.common.RateLimiterTest"`
예상: 컴파일 실패 (`RateLimiter` 없음)

- [ ] **Step 3: 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/common/RateLimiter.java`:
```java
package com.genderreveal.api.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter keyed by an arbitrary string (typically a guest cookie
 * value). Single-instance only — no shared cache — which fits this app's current scale (one API
 * process). Per-key locking, not a class-wide lock, so unrelated keys don't block each other.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Returns true and records a hit if under the limit; returns false (no hit recorded) otherwise. */
    public boolean allow(String key, int maxHits, Duration window) {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(window);
        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxHits) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/common/RateLimitExceededException.java`:
```java
package com.genderreveal.api.common;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/common/RateLimitExceptionHandler.java`:
```java
package com.genderreveal.api.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RateLimitExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimitExceeded(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, "60")
            .body(Map.of("error", ex.getMessage()));
    }
}
```

`GuessService.java`: 생성자에 `RateLimiter rateLimiter` 필드를 추가하고, `create` 맨 앞(페이지 조회보다 먼저)에 체크를 추가한다:
```java
    private static final int MAX_GUESSES_PER_MINUTE = 5;

    public Guess create(String slug, String guestCookieId, String guessedGender) {
        if (!rateLimiter.allow("guess:" + guestCookieId, MAX_GUESSES_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new RateLimitExceededException("Too many guesses — try again in a minute");
        }

        Page page = pageRepository.findBySlug(slug)
```
(`import com.genderreveal.api.common.RateLimitExceededException;`, `import com.genderreveal.api.common.RateLimiter;`, `import java.time.Duration;`를 추가하고, 생성자 시그니처에 `RateLimiter rateLimiter`를 추가해 필드에 대입한다.)

`GuestbookEntryService.java`: 마찬가지로 `RateLimiter`를 주입하고 `create` 맨 앞에 체크를 추가한다. 이 서비스는 쿠키가 없거나 무효면 영속화 시 `null`을 쓰지만(Plan 3 설계 유지), 레이트리밋 키는 컨트롤러가 넘겨주는 별도의 "레이트리밋용 식별자"를 쓴다 — 아래처럼 `create`에 인자를 하나 추가한다:
```java
    private static final int MAX_ENTRIES_PER_MINUTE = 5;

    public GuestbookEntry create(String slug, String nickname, String message, String guestCookieId,
                                  String rateLimitKey) {
        if (!rateLimiter.allow("guestbook:" + rateLimitKey, MAX_ENTRIES_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new RateLimitExceededException("Too many messages — try again in a minute");
        }
        Page page = requireOpenPage(slug);
        GuestbookEntry entry = new GuestbookEntry(page.getId(), nickname, message, guestCookieId, Instant.now(clock));
        return guestbookEntryRepository.save(entry);
    }
```

`GuestController`가 아니라 `GuessController.java`는 변경 없음(레이트리밋 로직이 서비스에만 있고, 서비스는 이미 컨트롤러가 넘기는 `guestId`—`guestCookie.resolve(...)`로 항상 유효한 UUID—를 그대로 키로 쓴다).

`GuestbookEntryController.java`는 레이트리밋 키를 위해 `GuestCookie`로 쿠키를 **해석(resolve)**해서 서비스에 넘긴다(영속화되는 `guestCookieId`는 기존 로직대로 "유효한 기존 쿠키일 때만" 그대로 유지 — 이 둘은 다른 값일 수 있다: 쿠키가 없던 방문자는 레이트리밋 키로는 방금 만든 UUID를 쓰지만, 방명록에는 `guestCookieId=null`로 저장된다):
```java
    @PostMapping
    public ResponseEntity<GuestbookEntryResponse> create(
            @PathVariable String slug,
            @CookieValue(name = GuestCookie.NAME, required = false) String existingGuestId,
            @Valid @RequestBody GuestbookEntryCreateRequest request) {
        String guestId = guestCookie.isValid(existingGuestId) ? existingGuestId : null;
        String rateLimitKey = guestCookie.resolve(existingGuestId);
        GuestbookEntry entry = guestbookEntryService.create(slug, request.nickname(), request.message(), guestId, rateLimitKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(GuestbookEntryResponse.from(entry));
    }
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/common/RateLimiter.java api/src/main/java/com/genderreveal/api/common/RateLimitExceededException.java api/src/main/java/com/genderreveal/api/common/RateLimitExceptionHandler.java api/src/main/java/com/genderreveal/api/guess/ api/src/main/java/com/genderreveal/api/guestbook/ api/src/test/java/com/genderreveal/api/common/RateLimiterTest.java api/src/test/java/com/genderreveal/api/guess/GuessControllerTest.java api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java
git commit -m "feat(api): 게스트 쿠키 기준 레이트리밋(맞추기·방명록 작성 분당 5회) 추가"
```

---

### Task 2: 백엔드 — 검증 오류 JSON 형식 통일

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/common/ValidationExceptionHandler.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/common/ValidationExceptionHandlerTest.java`

**Interfaces:**
- Produces: `@Valid` 실패(`MethodArgumentNotValidException`) → 400 `{"error": "<첫 번째 필드 오류 메시지>"}` (기존 도메인 예외 핸들러와 같은 형태)

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/common/ValidationExceptionHandlerTest.java`:
```java
package com.genderreveal.api.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ValidationExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void invalidMagicLinkEmailReturnsTheSharedErrorShape() throws Exception {
        mockMvc.perform(post("/api/auth/magic-link")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$.timestamp").doesNotExist());
    }
}
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.common.ValidationExceptionHandlerTest"`
예상: 실패(Spring 기본 오류 바디는 `$.error` 대신 `$.errors`/`$.timestamp` 등을 씀)

- [ ] **Step 3: 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/common/ValidationExceptionHandler.java`:
```java
package com.genderreveal.api.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Only handles the one framework exception `@Valid` throws — not a generic catch-all — so every
 * 400 in this API (domain and validation) shares the same {"error": "..."} shape.
 */
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError firstError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = firstError != null
            ? firstError.getField() + ": " + firstError.getDefaultMessage()
            : "Validation failed";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS — 기존에 400을 기대하던 테스트들은 상태 코드만 확인했다면 영향 없음. 혹시 `$.errors` 등 옛 형태를 단언하는 테스트가 있으면 `$.error` 존재 확인으로 최소 수정한다.

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/common/ValidationExceptionHandler.java api/src/test/java/com/genderreveal/api/common/ValidationExceptionHandlerTest.java
git commit -m "feat(api): 검증 오류 응답을 도메인 예외와 같은 {error} 형식으로 통일"
```

---

### Task 3: 백엔드 — 만료 토큰/세션 정리 스케줄러 + 보관주기 연장 원자적 갱신

**Files:**
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/MagicLinkTokenRepository.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerSessionRepository.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/ExpiredCredentialCleanupService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/GenderRevealApiApplication.java`(또는 `@SpringBootApplication` 클래스 — `@EnableScheduling` 추가)
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageRepository.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageService.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/auth/ExpiredCredentialCleanupServiceTest.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/owner/OwnerPageControllerTest.java`

**Interfaces:**
- Produces: `MagicLinkTokenRepository.deleteByExpiresAtBefore(Instant): int`, `OwnerSessionRepository.deleteByExpiresAtBefore(Instant): int`, `ExpiredCredentialCleanupService.purgeExpired()`(`@Scheduled`로 매일 1회 호출, 테스트에서 직접 호출 가능), `PageRepository.markExtended(Long id, String newExpiresAt): int`(원자적 UPDATE), `OwnerPageService.extend`가 이걸 써서 동시 연장 요청 중 한쪽만 성공하게 함

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/auth/ExpiredCredentialCleanupServiceTest.java`:
```java
package com.genderreveal.api.auth;

import com.genderreveal.api.config.MutableTestClock;
import com.genderreveal.api.config.MutableTestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
@Transactional
class ExpiredCredentialCleanupServiceTest {

    @Autowired
    private ExpiredCredentialCleanupService cleanupService;

    @Autowired
    private MagicLinkTokenRepository tokenRepository;

    @Autowired
    private OwnerSessionRepository sessionRepository;

    @Autowired
    private Clock clock;

    @Test
    void purgesOnlyExpiredTokensAndSessions() {
        Instant now = Instant.now(clock);
        tokenRepository.save(new MagicLinkToken("expired@example.com", "hash-expired", now.minus(1, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS)));
        MagicLinkToken fresh = tokenRepository.save(new MagicLinkToken("fresh@example.com", "hash-fresh", now.plus(10, ChronoUnit.MINUTES), now));
        sessionRepository.save(new OwnerSession("expired@example.com", "sess-hash-expired", now.minus(1, ChronoUnit.DAYS), now.minus(8, ChronoUnit.DAYS)));
        OwnerSession freshSession = sessionRepository.save(new OwnerSession("fresh@example.com", "sess-hash-fresh", now.plus(7, ChronoUnit.DAYS), now));

        cleanupService.purgeExpired();

        assertThat(tokenRepository.findByTokenHash("hash-expired")).isEmpty();
        assertThat(tokenRepository.findById(fresh.getId())).isPresent();
        assertThat(sessionRepository.findBySessionTokenHash("sess-hash-expired")).isEmpty();
        assertThat(sessionRepository.findById(freshSession.getId())).isPresent();
    }
}
```

`OwnerPageControllerTest.java`에 동시 연장 테스트를 추가한다(기존 헬퍼 재사용):
```java
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
```
(이 테스트는 순차 호출이라 기존 `ExtensionAlreadyUsedException` 흐름과 겉보기엔 같지만, 이번 태스크의 목적은 **내부 구현**이 체크-후-저장에서 원자적 UPDATE로 바뀌어도 이 동작이 그대로 유지되는지 확인하는 회귀 테스트다.)

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.auth.ExpiredCredentialCleanupServiceTest"`
예상: 컴파일 실패

- [ ] **Step 3: 구현**

`MagicLinkTokenRepository.java`에 추가:
```java
    int deleteByExpiresAtBefore(Instant cutoff);
```

`OwnerSessionRepository.java`에 추가 (`import java.time.Instant;`):
```java
    int deleteByExpiresAtBefore(Instant cutoff);
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/ExpiredCredentialCleanupService.java`:
```java
package com.genderreveal.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class ExpiredCredentialCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ExpiredCredentialCleanupService.class);

    private final MagicLinkTokenRepository tokenRepository;
    private final OwnerSessionRepository sessionRepository;
    private final Clock clock;

    public ExpiredCredentialCleanupService(MagicLinkTokenRepository tokenRepository,
                                            OwnerSessionRepository sessionRepository, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    /** Runs once a day; also callable directly (e.g. from tests). */
    @Scheduled(cron = "0 0 4 * * *")
    public void purgeExpired() {
        Instant now = Instant.now(clock);
        int tokens = tokenRepository.deleteByExpiresAtBefore(now);
        int sessions = sessionRepository.deleteByExpiresAtBefore(now);
        if (tokens > 0 || sessions > 0) {
            log.info("Purged {} expired magic-link token(s) and {} expired owner session(s)", tokens, sessions);
        }
    }
}
```

`gender-reveal` API의 `@SpringBootApplication` 클래스(파일명은 `find api/src/main/java -name "*Application.java"`로 확인)에 `@EnableScheduling`을 추가한다:
```java
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class GenderRevealApiApplication {
```

`PageRepository.java`에 원자적 연장 쿼리를 추가한다 (`import org.springframework.data.jpa.repository.Modifying;`, `import org.springframework.data.jpa.repository.Query;`, `import org.springframework.data.repository.query.Param;`, `import org.springframework.transaction.annotation.Transactional;`):
```java
    /** Atomically flips extended false→true; returns 0 if already extended (lost the race or a repeat call). */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Page p set p.expiresAt = :newExpiresAt, p.extended = true where p.id = :id and p.extended = false")
    int markExtended(@Param("id") Long id, @Param("newExpiresAt") java.time.Instant newExpiresAt);
```

`OwnerPageService.java`의 `extend`를 다음으로 바꾼다:
```java
    public OwnerPageSummary extend(String slug, String ownerEmail) {
        Page page = requireOwned(slug, ownerEmail);
        Instant now = Instant.now(clock);
        Instant base = page.getExpiresAt().isAfter(now) ? page.getExpiresAt() : now;
        Instant newExpiresAt = base.plus(EXTENSION_DAYS, ChronoUnit.DAYS);

        if (pageRepository.markExtended(page.getId(), newExpiresAt) == 0) {
            throw new ExtensionAlreadyUsedException(slug);
        }

        Page saved = pageRepository.findById(page.getId()).orElseThrow(() -> new PageNotFoundException(slug));
        return OwnerPageSummary.of(saved, statusCalculator.calculate(saved, now));
    }
```
(`import com.genderreveal.api.page.PageNotFoundException;`가 없으면 추가. `page.extend(...)`를 직접 호출하던 기존 코드는 제거 — `Page.extend(Instant)` 도메인 메서드 자체는 다른 곳에서 안 쓰이면 남겨둬도 무방하다.)

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/auth/ api/src/main/java/com/genderreveal/api/page/PageRepository.java api/src/main/java/com/genderreveal/api/owner/OwnerPageService.java api/src/test/java/com/genderreveal/api/auth/ExpiredCredentialCleanupServiceTest.java api/src/test/java/com/genderreveal/api/owner/OwnerPageControllerTest.java
git commit -m "feat(api): 만료 토큰/세션 정리 스케줄러 추가, 보관주기 연장을 원자적 UPDATE로 변경"
```

---

### Task 4: 프론트 — 인트로 자동전환 타이머를 태명 길이에 비례해 연장

**Files:**
- Modify: `gender-reveal/web/src/components/screens/IntroScreen.tsx`
- Modify: `gender-reveal/web/src/components/screens/IntroScreen.test.tsx`

**Interfaces:**
- Produces: `autoAdvanceMs` 기본값이 `text.length * speedMs(70) + 2000`으로 계산됨(기존처럼 `autoAdvanceMs` prop을 명시하면 그 값이 우선)

- [ ] **Step 1: 실패하는 테스트 작성**

`IntroScreen.test.tsx`에 테스트를 추가한다(기존 파일의 `beforeEach(() => vi.useFakeTimers(...))` 등을 그대로 재사용):
```tsx
  it('extends the auto-advance delay for a long nickname instead of cutting off the typing', () => {
    const onNext = vi.fn();
    const longNickname = '가'.repeat(30);
    render(<IntroScreen nickname={longNickname} zodiac={null} onNext={onNext} />);

    // Full text is much longer than the old fixed 5000ms would allow to finish typing at 70ms/char.
    act(() => { vi.advanceTimersByTime(4999); });
    expect(onNext).not.toHaveBeenCalled();
  });
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 실패(기존 고정 5000ms 기본값 때문에 4999ms 시점에서도 아직 안 불렸어야 하는데, 실제로는 긴 이름이어도 5000ms에 못 미치는 시점이라 이 특정 케이스는 우연히 통과할 수 있음 — 구현자는 `text.length`가 충분히 크도록(예: 이름 30자) 조정해 RED를 실제로 확인한다. 계획서 저자 주: 정확한 RED를 보장하려면 `longNickname`을 늘려 `text.length * 70 + 2000 > 5000`이 되도록(`text.length > ~43`) 맞춘다.)

- [ ] **Step 3: 구현**

`IntroScreen.tsx`를 수정한다:
```tsx
export function IntroScreen({
  nickname,
  zodiac,
  onNext,
  autoAdvanceMs,
}: {
  nickname: string;
  zodiac: ZodiacKey | null;
  onNext: () => void;
  autoAdvanceMs?: number;
}) {
  const reduced = usePrefersReducedMotion();
  const text = `두근두근...\n${topic(nickname)} 딸일까요,\n아들일까요?`;
  const { shown } = useTypewriter(text, { instant: reduced });
  // Give the guest time to actually read the typed sentence: typing time + a fixed buffer,
  // so a long nickname doesn't get cut off by a fixed delay.
  const effectiveAutoAdvanceMs = autoAdvanceMs ?? text.length * 70 + 2000;

  useEffect(() => {
    const id = setTimeout(onNext, effectiveAutoAdvanceMs);
    return () => clearTimeout(id);
  }, [onNext, effectiveAutoAdvanceMs]);
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS. 기존 "advances by itself after the auto-advance delay" 테스트는 `autoAdvanceMs={5000}`을 명시로 전달하고 있어 그대로 통과해야 한다(확인만 하고, 깨졌으면 최소 수정).

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/components/screens/IntroScreen.tsx web/src/components/screens/IntroScreen.test.tsx
git commit -m "fix(web): 인트로 자동전환 타이머를 태명 길이에 비례해 연장(접근성)"
```

---

### Task 5: 프론트 — 결과 화면에 "이미 참여함" 안내 문구

**Files:**
- Modify: `gender-reveal/web/src/components/screens/ResultScreen.tsx`
- Modify: `gender-reveal/web/src/components/screens/ResultScreen.test.tsx`
- Modify: `gender-reveal/web/src/components/GuestPage.tsx`
- Modify: `gender-reveal/web/src/components/GuestPage.test.tsx`

**Interfaces:**
- Produces: `<ResultScreen ... alreadyGuessed />`(새 prop, 기본 `false`) → `alreadyGuessed`가 true면 예측 배지 위에 `이미 참여하셨어요 · 이전 예측을 보여드려요` 문구 표시; `GuestPage`가 `submitGuess`의 `alreadyGuessed` 값을 상태로 보관해 넘김

- [ ] **Step 1: 실패하는 테스트 작성**

`ResultScreen.test.tsx`에 테스트를 추가한다(기존 `reveal(...)` 헬퍼 재사용):
```tsx
  it('shows a notice when the guest already participated before', async () => {
    render(<ResultScreen page={basePage} guess="boy" onNext={() => {}} alreadyGuessed />);

    await reveal('선물상자를 열어보세요');

    expect(screen.getByText('이미 참여하셨어요 · 이전 예측을 보여드려요')).toBeInTheDocument();
  });

  it('omits the notice for a first-time guess', async () => {
    render(<ResultScreen page={basePage} guess="boy" onNext={() => {}} />);

    await reveal('선물상자를 열어보세요');

    expect(screen.queryByText('이미 참여하셨어요 · 이전 예측을 보여드려요')).not.toBeInTheDocument();
  });
```

`GuestPage.test.tsx`에 다음을 추가한다(기존 "uses the earlier guess when the guest already participated" 테스트가 있다면 그 뒤에 단언을 덧붙이고, 없다면 기존 유사 테스트 이름을 확인해 맞춰 넣는다):
```tsx
    expect(await screen.findByText('이미 참여하셨어요 · 이전 예측을 보여드려요', {}, { timeout: 3000 })).toBeInTheDocument();
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 실패(문구 없음)

- [ ] **Step 3: 구현**

`ResultScreen.tsx`의 props와 렌더 부분을 수정한다:
```tsx
export function ResultScreen({
  page,
  guess,
  onNext,
  alreadyGuessed = false,
  revealDelayMs = 900,
}: {
  page: OpenPageView;
  guess: Gender;
  onNext: () => void;
  alreadyGuessed?: boolean;
  revealDelayMs?: number;
}) {
```
`guessSummary(...)`를 렌더하는 `<p>` 바로 앞에 추가:
```tsx
      {alreadyGuessed && (
        <p className="text-body-sm text-ink-muted">이미 참여하셨어요 · 이전 예측을 보여드려요</p>
      )}
```

`GuestPage.tsx`: `guess` 상태 옆에 `alreadyGuessed` 상태를 추가하고, `onSelect`와 `ResultScreen` 호출부를 수정한다:
```tsx
  const [guess, setGuess] = useState<Gender | null>(null);
  const [alreadyGuessed, setAlreadyGuessed] = useState(false);
```
```tsx
      const result = await submitGuess(slug, gender);
      setGuess(result.guessedGender);
      setAlreadyGuessed(result.alreadyGuessed);
      setStage('result');
```
```tsx
      <ResultScreen
        page={view}
        guess={guess}
        alreadyGuessed={alreadyGuessed}
        onNext={() => {
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/components/screens/ResultScreen.tsx web/src/components/screens/ResultScreen.test.tsx web/src/components/GuestPage.tsx web/src/components/GuestPage.test.tsx
git commit -m "feat(web): 재방문 게스트에게 결과 화면에서 '이미 참여함' 안내"
```

---

### Task 6: 프론트 — 잔여 견고화 (타입 있는 예외, 토큰 교차검증)

**Files:**
- Modify: `gender-reveal/web/src/lib/api.ts`
- Modify: `gender-reveal/web/src/lib/api.test.ts`
- Modify: `gender-reveal/web/src/lib/tokens.test.ts`
- Modify: `gender-reveal/CLAUDE.md`

**Interfaces:**
- Produces: `request()`의 성공 경로가 비-JSON 2xx 바디에서 원시 `SyntaxError` 대신 `ApiError(status, null)`를 던짐; `tokens.test.ts`가 `planning/design-system/project/tokens.json`을 직접 읽어 `tailwind.config.ts`와 대조

- [ ] **Step 1: 실패하는 테스트 작성**

`api.test.ts`에 추가 (기존 `respond`/`fetchMock` 헬퍼 재사용 — `respond`가 텍스트 바디를 만들 수 없다면 새 헬퍼를 하나 추가한다):
```ts
  it('wraps a non-JSON 2xx body as a typed ApiError instead of throwing a raw SyntaxError', async () => {
    fetchMock.mockReturnValue(
      Promise.resolve(new Response('not json', { status: 200, headers: { 'Content-Type': 'text/plain' } })),
    );

    await expect(getPage('s')).rejects.toBeInstanceOf(ApiError);
  });
```

`tokens.test.ts`를 다음으로 교체(기존 하드코딩 비교 테스트는 유지하되, 실제 `tokens.json`과의 교차검증을 추가):
```ts
import fs from 'node:fs';
import path from 'node:path';
import config from '../../tailwind.config';

const colors = config.theme?.extend?.colors as Record<string, string>;

describe('design tokens in tailwind config', () => {
  it('carries the CTA and gender accent colors from tokens.json', () => {
    expect(colors['accent-primary']).toBe('#F2793A');
    expect(colors['accent-boy']).toBe('#6F86E6');
    expect(colors['accent-girl']).toBe('#E37AA6');
    expect(colors['surface-50']).toBe('#FFFBF6');
  });

  it('defines the display and body font families and radius tokens', () => {
    const extend = config.theme?.extend as Record<string, Record<string, unknown>>;
    expect(extend.fontFamily.display).toEqual(['var(--font-hi-melody)', 'system-ui', 'sans-serif']);
    expect(extend.borderRadius['radius-md']).toBe('14px');
  });

  it('matches every color token in the design-system source of truth', () => {
    const tokensPath = path.resolve(__dirname, '../../../planning/design-system/project/tokens.json');
    const tokens = JSON.parse(fs.readFileSync(tokensPath, 'utf-8'));
    const sourceColors: { name: string; value: string }[] = tokens.color.tokens;

    for (const { name, value } of sourceColors) {
      if (name.startsWith('illus-')) continue; // illustration-only tokens are not ported to Tailwind
      expect(colors[name], `tailwind.config.ts is missing or wrong for token "${name}"`).toBe(value);
    }
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 첫 번째 테스트 실패(`SyntaxError`가 `ApiError`가 아님)

- [ ] **Step 3: 구현**

`api.ts`의 `request()` 마지막 부분을 다음으로 바꾼다:
```ts
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new ApiError(response.status, null);
  }
```

(프론트에는 게스트 쿠키 정규형 검사 로직이 없다 — 쿠키 검증은 백엔드 `GuestCookie.java`에만 있고 프론트는 쿠키 값을 직접 읽지 않으므로, 이 항목은 프론트에서 할 일이 없어 스킵한다.)

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: CLAUDE.md 하드닝 목록 정리**

`gender-reveal/CLAUDE.md`의 "Known deferred hardening (frontend, `web/`)" 항목에서 이번에 처리한 것들(토큰 교차검증, `request()` 타입 예외, IntroScreen 타이머, `alreadyGuessed` 미노출)을 지우고, 남는 것(`make-og.mjs` trim 취약점, `actualGender` 사전 노출은 계속 보류로 유지)만 남긴다. 백엔드 목록에서도 레이트리밋·검증 오류 형식·정리 스케줄러 항목을 지운다.

- [ ] **Step 6: 커밋**

```bash
cd gender-reveal
git add web/src/lib/api.ts web/src/lib/api.test.ts web/src/lib/tokens.test.ts CLAUDE.md
git commit -m "fix(web): 비-JSON 2xx 응답을 타입 있는 ApiError로, 디자인 토큰을 소스와 교차검증"
```

---

## 이 계획 완료 후 상태

- 맞추기·방명록 작성이 게스트 쿠키 기준으로 분당 5회 제한된다.
- 모든 400 응답(도메인 예외 + 검증 실패)이 `{"error": "..."}` 형태로 통일된다.
- 만료된 매직링크 토큰·소유자 세션이 매일 자동 정리되고, 보관주기 연장이 동시 요청에도 정확히 1회만 성공한다.
- 인트로 화면 자동전환이 태명 길이에 맞춰 늘어나고, 결과 화면이 재방문 게스트에게 "이미 참여함"을 알려준다.
- 디자인 토큰이 실제 소스(`tokens.json`)와 자동으로 맞는지 테스트가 검증한다.
- 여전히 보류: 쿠키 `Secure` 플래그(TLS/Docker 태스크 때), 맞추기 전 정답 노출(별도 설계 필요), 방명록 페이지네이션.

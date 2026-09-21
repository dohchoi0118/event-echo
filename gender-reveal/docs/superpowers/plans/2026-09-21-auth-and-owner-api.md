# 젠더리빌 인증 + 소유자 API 구현 계획 (Plan 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이메일 매직링크 로그인(소유자 검증), 로그인 뒤에만 가능한 페이지 생성, 방문자 수 집계, 소유자용 통계/방명록 관리/보관주기 연장 API를 만든다.

**Architecture:** Spring Security 없이 세션 쿠키를 읽는 `HandlerInterceptor`와 `OwnerPrincipal` 인자 리졸버로 소유자를 식별한다. 세션과 매직링크 토큰은 이메일에 붙고(페이지가 아님) 토큰은 SHA-256 해시로만 저장한다. 이메일 발송은 `EmailSender` 인터페이스로 분리해 운영은 Resend, 개발·테스트는 로그/기록용 구현을 쓴다. 소유자 API는 `com.genderreveal.api.owner`, 인증은 `com.genderreveal.api.auth`, 이메일은 `com.genderreveal.api.email` 패키지에 둔다.

**Tech Stack:** Plan 1·2와 동일 (Java 21, Spring Boot 3.3, Spring Data JPA, SQLite, Flyway). 신규 의존성 없음 — Resend 호출은 JDK `java.net.http.HttpClient`를 쓴다.

**Spec:** [docs/superpowers/specs/2026-09-18-implementation-design.md](../specs/2026-09-18-implementation-design.md) — 5·6절이 이 계획으로 아래처럼 바뀐다 (스펙 문서 11절에도 같은 내용을 추가했다).

## 스펙에서 바뀐 결정 (사용자와 합의)

- **로그인 먼저, 그다음 페이지 생성.** `POST /api/pages`는 소유자 세션이 필수이고, `ownerEmail`은 요청 본문이 아니라 **세션에서** 가져온다. 남의 이메일로 페이지를 만들 수 없다.
- **세션은 페이지가 아니라 이메일에 붙는다.** 로그인 시점에는 페이지가 없으므로 V1의 `owner_sessions.page_id`를 `owner_email`로 바꾼다. 대시보드 권한은 "세션 이메일 == 페이지의 `ownerEmail`"로 판정한다.
- **방문자 수는 쿠키 기준 고유 방문자.** 방문자가 `open` 상태 페이지를 처음 열 때 기존 `guest_id` 쿠키를 발급하고 `page_visits`에 `(page_id, guest_cookie_id)`를 한 번만 기록한다. 방문자는 여전히 로그인이 필요 없다.
- **소유자 대시보드 방명록에는 작성자의 맞추기 결과가 표시된다.** 방명록 작성 시 `guest_id` 쿠키를 `guestbook_entries.guest_cookie_id`(nullable)에 저장하고, 소유자 조회에서만 `guesses`와 이어 "예측: 남아 · 정답/오답/예측 안 함"을 계산한다. 게스트용 공개 방명록은 그대로다.

## 이 계획에서 스펙을 넘어 내린 설계 판단

- 매직링크 요청은 이메일 존재 여부와 무관하게 항상 202를 준다(계정 열거 방지). 같은 이메일로 60초 안에 다시 요청하면 조용히 메일을 보내지 않는다. 메일 발송이 실패해도 202를 유지하고 서버 로그에만 남긴다.
- 토큰은 32바이트 랜덤(URL-safe Base64), 유효 15분, 1회용(조건부 UPDATE로 원자적 소비). 세션은 7일.
- 남의 페이지/방명록 항목에 접근하면 403이 아니라 **404**로 응답해 존재 여부가 새지 않게 한다.
- 연장은 `max(현재 시각, 기존 만료일) + 30일`, 1회 한정(이미 `extended`면 409). 이미 만료된 페이지도 연장할 수 있다.
- 이메일 주소는 `trim + 소문자`로 정규화해 저장한다.
- 발행 링크 메일(`{baseUrl}/g/{slug}`)은 페이지 생성 직후 보내되, 발송 실패가 생성을 실패시키지 않는다.
- 검증 에러 JSON 형식 통일, 쿠키 `Secure` 기본값, 레이트리밋, 방명록 페이지네이션은 이 계획의 범위 밖이다(하드닝 목록에 유지). `Secure`는 `app.session-cookie-secure` 설정값으로만 켤 수 있게 열어둔다(기본 false).

## Global Constraints

- Java 21, Gradle, Spring Boot 3.3, SQLite `foreign_keys=on` (Plan 1과 동일). 새 테이블/컬럼은 반드시 Flyway 마이그레이션으로만 추가한다 (`ddl-auto: none`).
- 시간은 항상 주입된 `Clock`(`Instant.now(clock)`)으로 얻는다. `Instant.now()`를 서비스/컨트롤러에서 직접 호출하지 않는다. 테스트가 `MutableTestClockConfig`를 `@Import`하면 시계가 실제 시각과 어긋날 수 있으므로 **그런 테스트는 기준 시각을 항상 주입된 `clock.instant()`에서 얻는다**.
- `Instant` 컬럼은 `InstantStringConverter`(`com.genderreveal.api.common`)를 반드시 적용한다.
- 토큰(매직링크, 세션)은 평문을 DB에 저장하지 않는다. SHA-256 hex 해시만 저장한다.
- 소유자 인증이 필요한 경로: `POST /api/pages`, `GET /api/auth/me`, `/api/owner/**`. 세션이 없거나 만료면 `401` + `{"error":"Unauthorized"}`.
- 도메인 예외는 이름이 명확한 전용 타입으로 던지고, 각 패키지의 `@RestControllerAdvice`는 자기 패키지의 전용 타입만 처리한다. `DataAccessException` 같은 범용 타입을 전역 핸들러로 잡지 않는다(Plan 2 Task 3 결정).
- SQLite 유니크 제약 위반은 `DataIntegrityViolationException`이 아니라 `JpaSystemException`(= `DataAccessException`)으로 올라온다. 레이스 백스톱 catch는 `DataAccessException`을 잡는다(Plan 2 최종 리뷰 결정).
- 패키지 루트: `com.genderreveal.api`. 모든 명령은 `gender-reveal/api`에서 `./gradlew`로 실행한다.

## 파일 구조 요약

```
api/src/main/java/com/genderreveal/api/
  config/    AppProperties, AppConfig
  email/     EmailSender, LoggingEmailSender, ResendEmailSender, EmailSendException
  auth/      TokenGenerator, TokenHasher, MagicLinkToken(+Repository), MagicLinkService, MagicLinkRequest,
             OwnerSession(+Repository), OwnerSessionService, OwnerSessionCookie, OwnerPrincipal,
             OwnerAuthInterceptor, OwnerPrincipalArgumentResolver, WebConfig, AuthController
  common/    GuestCookie
  visit/     PageVisit, PageVisitRepository, VisitService
  owner/     OwnerPageService, OwnerPageController, OwnerPageSummary, PageStats,
             OwnerGuestbookService, OwnerGuestbookController, OwnerGuestbookEntryResponse, GuestbookHiddenRequest,
             ExtensionAlreadyUsedException, OwnerExceptionHandler
  guestbook/ GuestbookEntryNotFoundException (신규)
api/src/main/resources/db/migration/V2__owner_auth_and_visits.sql
api/src/test/java/com/genderreveal/api/
  email/RecordingEmailSender, auth/OwnerTestSupport (테스트 전용 빈)
```

---

### Task 1: V2 마이그레이션 (이메일 세션, 방문 테이블, 방명록 쿠키 컬럼)

**Files:**
- Create: `gender-reveal/api/src/main/resources/db/migration/V2__owner_auth_and_visits.sql`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/migration/FlywayMigrationTest.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/migration/SchemaV2Test.java`

**Interfaces:**
- Produces: 테이블 `page_visits(id, page_id FK, guest_cookie_id, created_at, UNIQUE(page_id, guest_cookie_id))`, `owner_sessions(id, owner_email, session_token_hash UNIQUE, expires_at, created_at)`, `magic_link_tokens(id, owner_email, token_hash UNIQUE, expires_at, used, created_at)`, 컬럼 `guestbook_entries.guest_cookie_id TEXT NULL`

V1의 `owner_sessions`/`magic_link_tokens`는 아직 어떤 코드도 쓰지 않고 실제 데이터도 없으므로 `DROP` 후 재생성한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/migration/FlywayMigrationTest.java`의 기대 테이블 목록을 다음으로 교체한다 (`containsExactlyInAnyOrder` 인자에 `"page_visits"` 추가):
```java
        assertThat(tableNames).containsExactlyInAnyOrder(
            "pages", "guesses", "guestbook_entries", "magic_link_tokens", "owner_sessions", "page_visits"
        );
```

`gender-reveal/api/src/test/java/com/genderreveal/api/migration/SchemaV2Test.java`:
```java
package com.genderreveal.api.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaV2Test {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerSessionsAreKeyedByEmailNotPage() {
        assertThat(columns("owner_sessions"))
            .contains("owner_email", "session_token_hash", "expires_at", "created_at")
            .doesNotContain("page_id");
    }

    @Test
    void magicLinkTokensNoLongerReferenceAPage() {
        assertThat(columns("magic_link_tokens"))
            .contains("owner_email", "token_hash", "expires_at", "used", "created_at")
            .doesNotContain("page_id");
    }

    @Test
    void guestbookEntriesHaveNullableGuestCookieId() {
        assertThat(columns("guestbook_entries")).contains("guest_cookie_id");
    }

    @Test
    void pageVisitsRejectsOrphanPageAndDuplicateVisitor() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO page_visits (page_id, guest_cookie_id, created_at) VALUES (999999, 'g', 't')"))
            .isInstanceOf(DataAccessException.class);
    }

    private List<String> columns(String table) {
        return jdbcTemplate.queryForList("SELECT name FROM pragma_table_info('" + table + "')", String.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.migration.*"`
예상: `FlywayMigrationTest`(page_visits 없음)와 `SchemaV2Test` 실패

- [ ] **Step 3: 마이그레이션 작성**

`gender-reveal/api/src/main/resources/db/migration/V2__owner_auth_and_visits.sql`:
```sql
DROP TABLE owner_sessions;
CREATE TABLE owner_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_email TEXT NOT NULL,
    session_token_hash TEXT NOT NULL UNIQUE,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_owner_sessions_owner_email ON owner_sessions(owner_email);

DROP TABLE magic_link_tokens;
CREATE TABLE magic_link_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_email TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TEXT NOT NULL,
    used INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_magic_link_tokens_owner_email ON magic_link_tokens(owner_email, created_at);

CREATE TABLE page_visits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    page_id INTEGER NOT NULL REFERENCES pages(id),
    guest_cookie_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE (page_id, guest_cookie_id)
);

ALTER TABLE guestbook_entries ADD COLUMN guest_cookie_id TEXT;
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS (기존 테스트 회귀 없음)

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/resources/db/migration/V2__owner_auth_and_visits.sql api/src/test/java/com/genderreveal/api/migration/
git commit -m "feat(api): V2 마이그레이션 — 이메일 기반 세션, 방문 기록, 방명록 게스트 쿠키"
```

---

### Task 2: 이메일 발송 추상화 (Resend + 로그 구현 + 테스트용 기록 구현)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/config/AppProperties.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/config/AppConfig.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/email/EmailSender.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/email/EmailSendException.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/email/LoggingEmailSender.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/email/ResendEmailSender.java`
- Modify: `gender-reveal/api/src/main/resources/application.yml`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/email/RecordingEmailSender.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/email/ResendEmailSenderTest.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/config/AppConfigTest.java`

**Interfaces:**
- Produces: `EmailSender.send(String to, String subject, String textBody)`, `EmailSendException`, `AppProperties(String baseUrl, boolean sessionCookieSecure, Resend resend)` with `Resend(String apiKey, String from, String apiUrl)`, 테스트 빈 `RecordingEmailSender`(`@Primary`, `sent(): List<SentEmail>`, `clear()`, `record SentEmail(String to, String subject, String body)`)

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/email/ResendEmailSenderTest.java`:
```java
package com.genderreveal.api.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailSenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsJsonWithBearerKeyToResend() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        String url = startServer(200, authorization, body);

        new ResendEmailSender(url, "re_test_key", "젠더리빌 <noreply@example.com>", objectMapper)
            .send("owner@example.com", "제목", "본문");

        assertThat(authorization.get()).isEqualTo("Bearer re_test_key");
        JsonNode json = objectMapper.readTree(body.get());
        assertThat(json.get("from").asText()).isEqualTo("젠더리빌 <noreply@example.com>");
        assertThat(json.get("to").get(0).asText()).isEqualTo("owner@example.com");
        assertThat(json.get("subject").asText()).isEqualTo("제목");
        assertThat(json.get("text").asText()).isEqualTo("본문");
    }

    @Test
    void nonSuccessResponseBecomesEmailSendException() throws Exception {
        String url = startServer(500, new AtomicReference<>(), new AtomicReference<>());

        ResendEmailSender sender = new ResendEmailSender(url, "k", "f@example.com", objectMapper);

        assertThatThrownBy(() -> sender.send("owner@example.com", "s", "b"))
            .isInstanceOf(EmailSendException.class);
    }

    private String startServer(int status, AtomicReference<String> authorization, AtomicReference<String> body)
            throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/emails", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/emails";
    }
}
```

`gender-reveal/api/src/test/java/com/genderreveal/api/config/AppConfigTest.java`:
```java
package com.genderreveal.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.email.LoggingEmailSender;
import com.genderreveal.api.email.ResendEmailSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    private final AppConfig config = new AppConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void blankApiKeyUsesLoggingSender() {
        AppProperties props = new AppProperties("http://x", false, new AppProperties.Resend("", "f", "http://u"));

        assertThat(config.emailSender(props, objectMapper)).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    void configuredApiKeyUsesResendSender() {
        AppProperties props = new AppProperties("http://x", false, new AppProperties.Resend("re_key", "f", "http://u"));

        assertThat(config.emailSender(props, objectMapper)).isInstanceOf(ResendEmailSender.class);
    }
}
```

`gender-reveal/api/src/test/java/com/genderreveal/api/email/RecordingEmailSender.java` (테스트 전용 빈 — 테스트 소스에 있어도 `@SpringBootApplication` 컴포넌트 스캔에 잡힌다. 아래 `@Primary`가 운영용 `EmailSender` 빈을 가린다):
```java
package com.genderreveal.api.email;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Test-only sender that records instead of sending. Tests call clear() in @BeforeEach. */
@Component
@Primary
public class RecordingEmailSender implements EmailSender {

    public record SentEmail(String to, String subject, String body) {}

    private final List<SentEmail> sent = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(String to, String subject, String textBody) {
        sent.add(new SentEmail(to, subject, textBody));
    }

    public List<SentEmail> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.email.*" --tests "com.genderreveal.api.config.AppConfigTest"`
예상: 컴파일 실패 (`EmailSender` 등 없음)

- [ ] **Step 3: 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/config/AppProperties.java`:
```java
package com.genderreveal.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl, boolean sessionCookieSecure, Resend resend) {

    public record Resend(String apiKey, String from, String apiUrl) {}
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/email/EmailSender.java`:
```java
package com.genderreveal.api.email;

public interface EmailSender {
    void send(String to, String subject, String textBody);
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/email/EmailSendException.java`:
```java
package com.genderreveal.api.email;

public class EmailSendException extends RuntimeException {
    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/email/LoggingEmailSender.java`:
```java
package com.genderreveal.api.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dev fallback used when no Resend API key is configured. Never use in production: it logs message bodies (login links). */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String textBody) {
        log.info("[email not sent — no RESEND_API_KEY] to={} subject={}\n{}", to, subject, textBody);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/email/ResendEmailSender.java`:
```java
package com.genderreveal.api.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ResendEmailSender implements EmailSender {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String apiUrl;
    private final String apiKey;
    private final String from;
    private final ObjectMapper objectMapper;

    public ResendEmailSender(String apiUrl, String apiKey, String from, ObjectMapper objectMapper) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.from = from;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(String to, String subject, String textBody) {
        String json;
        try {
            json = objectMapper.writeValueAsString(
                Map.of("from", from, "to", List.of(to), "subject", subject, "text", textBody));
        } catch (JsonProcessingException ex) {
            throw new EmailSendException("Failed to serialize email payload", ex);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new EmailSendException("Resend responded with status " + response.statusCode());
            }
        } catch (IOException ex) {
            throw new EmailSendException("Failed to call Resend", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Interrupted while calling Resend", ex);
        }
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/config/AppConfig.java`:
```java
package com.genderreveal.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.email.EmailSender;
import com.genderreveal.api.email.LoggingEmailSender;
import com.genderreveal.api.email.ResendEmailSender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    @Bean
    public EmailSender emailSender(AppProperties props, ObjectMapper objectMapper) {
        AppProperties.Resend resend = props.resend();
        if (resend == null || resend.apiKey() == null || resend.apiKey().isBlank()) {
            return new LoggingEmailSender();
        }
        return new ResendEmailSender(resend.apiUrl(), resend.apiKey(), resend.from(), objectMapper);
    }
}
```

`gender-reveal/api/src/main/resources/application.yml` 맨 아래에 추가:
```yaml
app:
  base-url: ${APP_BASE_URL:http://localhost:8080}
  session-cookie-secure: ${APP_SESSION_COOKIE_SECURE:false}
  resend:
    api-key: ${RESEND_API_KEY:}
    from: ${RESEND_FROM:젠더리빌 <onboarding@resend.dev>}
    api-url: https://api.resend.com/emails
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS (`@Primary RecordingEmailSender`와 `AppConfig`의 `EmailSender` 빈이 공존해도 컨텍스트가 정상 기동)

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/config/ api/src/main/java/com/genderreveal/api/email/ api/src/main/resources/application.yml api/src/test/java/com/genderreveal/api/email/ api/src/test/java/com/genderreveal/api/config/AppConfigTest.java
git commit -m "feat(api): 이메일 발송 추상화(Resend/로그/테스트 기록) 추가"
```

---

### Task 3: 매직링크 요청 (`POST /api/auth/magic-link`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/TokenGenerator.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/TokenHasher.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/MagicLinkToken.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/MagicLinkTokenRepository.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/MagicLinkRequest.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/MagicLinkService.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/AuthController.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/auth/MagicLinkRequestTest.java`

**Interfaces:**
- Consumes: `EmailSender`, `AppProperties`(Task 2), 테이블 `magic_link_tokens`(Task 1), `Clock`
- Produces: `TokenGenerator.newToken(): String`, `TokenHasher.sha256(String): String`, `MagicLinkToken`(getId/getOwnerEmail/getTokenHash/getExpiresAt/isUsed/getCreatedAt), `MagicLinkTokenRepository.findByTokenHash(String): Optional<MagicLinkToken>`, `existsByOwnerEmailAndCreatedAtAfter(String, Instant): boolean`, `markUsed(Long): int`, `MagicLinkService.request(String email)`, `MagicLinkService.consume(String rawToken): Optional<String>`(Task 4가 사용 — 이 태스크에서 함께 구현·테스트한다), `POST /api/auth/magic-link` → 202

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/auth/MagicLinkRequestTest.java`:
```java
package com.genderreveal.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.config.MutableTestClock;
import com.genderreveal.api.config.MutableTestClockConfig;
import com.genderreveal.api.email.RecordingEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
@Transactional
class MagicLinkRequestTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emails;

    @Autowired
    private Clock clock;

    @BeforeEach
    void resetEmails() {
        emails.clear();
    }

    @Test
    void sendsLoginLinkToNormalizedEmail() throws Exception {
        requestLink("Owner-A@Example.com").andExpect(status().isAccepted());

        assertThat(emails.sent()).hasSize(1);
        RecordingEmailSender.SentEmail mail = emails.sent().get(0);
        assertThat(mail.to()).isEqualTo("owner-a@example.com");
        assertThat(mail.body()).contains("/api/auth/callback?token=");
    }

    @Test
    void invalidEmailIsRejectedAndNothingIsSent() throws Exception {
        requestLink("not-an-email").andExpect(status().isBadRequest());

        assertThat(emails.sent()).isEmpty();
    }

    @Test
    void secondRequestWithinCooldownSendsNothingButStillReturns202() throws Exception {
        requestLink("owner-b@example.com").andExpect(status().isAccepted());
        requestLink("owner-b@example.com").andExpect(status().isAccepted());

        assertThat(emails.sent()).hasSize(1);
    }

    @Test
    void requestAfterCooldownSendsAgain() throws Exception {
        requestLink("owner-c@example.com").andExpect(status().isAccepted());

        MutableTestClock testClock = (MutableTestClock) clock;
        testClock.advanceTo(testClock.instant().plus(61, ChronoUnit.SECONDS));
        requestLink("owner-c@example.com").andExpect(status().isAccepted());

        assertThat(emails.sent()).hasSize(2);
    }

    private org.springframework.test.web.servlet.ResultActions requestLink(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/magic-link")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }
}
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.auth.MagicLinkRequestTest"`
예상: 컴파일 실패 또는 404

- [ ] **Step 3: 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/TokenGenerator.java`:
```java
package com.genderreveal.api.auth;

import java.security.SecureRandom;
import java.util.Base64;

public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {}

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/TokenHasher.java`:
```java
package com.genderreveal.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TokenHasher {

    private TokenHasher() {}

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/MagicLinkToken.java`:
```java
package com.genderreveal.api.auth;

import com.genderreveal.api.common.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "magic_link_tokens")
public class MagicLinkToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "created_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant createdAt;

    protected MagicLinkToken() {
        // JPA
    }

    public MagicLinkToken(String ownerEmail, String tokenHash, Instant expiresAt, Instant createdAt) {
        this.ownerEmail = ownerEmail;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.used = false;
    }

    public Long getId() { return id; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/MagicLinkTokenRepository.java`:
```java
package com.genderreveal.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, Long> {

    Optional<MagicLinkToken> findByTokenHash(String tokenHash);

    boolean existsByOwnerEmailAndCreatedAtAfter(String ownerEmail, Instant cutoff);

    /** Atomically consumes a token: returns 1 only for the single caller that flips used from false to true. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MagicLinkToken t set t.used = true where t.id = :id and t.used = false")
    int markUsed(@Param("id") Long id);
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/MagicLinkRequest.java`:
```java
package com.genderreveal.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MagicLinkRequest(@NotBlank @Email String email) {}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/MagicLinkService.java`:
```java
package com.genderreveal.api.auth;

import com.genderreveal.api.config.AppProperties;
import com.genderreveal.api.email.EmailSendException;
import com.genderreveal.api.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final MagicLinkTokenRepository tokenRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final Clock clock;

    public MagicLinkService(MagicLinkTokenRepository tokenRepository, EmailSender emailSender,
                             AppProperties appProperties, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.emailSender = emailSender;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Always returns normally so callers can answer 202 regardless of outcome (no account enumeration). */
    public void request(String email) {
        String normalized = normalize(email);
        Instant now = Instant.now(clock);

        if (tokenRepository.existsByOwnerEmailAndCreatedAtAfter(normalized, now.minus(COOLDOWN))) {
            return;
        }

        String rawToken = TokenGenerator.newToken();
        tokenRepository.save(new MagicLinkToken(normalized, TokenHasher.sha256(rawToken), now.plus(TOKEN_TTL), now));

        String link = appProperties.baseUrl() + "/api/auth/callback?token=" + rawToken;
        String body = "아래 링크를 누르면 로그인돼요. 링크는 15분 동안, 한 번만 쓸 수 있어요.\n\n" + link
            + "\n\n본인이 요청하지 않았다면 이 메일을 무시해 주세요.";
        try {
            emailSender.send(normalized, "젠더리빌 로그인 링크", body);
        } catch (EmailSendException ex) {
            log.error("Failed to send magic link email", ex);
        }
    }

    /** Returns the owner email if the token is valid, unused and unexpired; consumes it atomically. */
    public Optional<String> consume(String rawToken) {
        Instant now = Instant.now(clock);
        return tokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))
            .filter(token -> !token.isUsed() && token.getExpiresAt().isAfter(now))
            .filter(token -> tokenRepository.markUsed(token.getId()) == 1)
            .map(MagicLinkToken::getOwnerEmail);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/AuthController.java`:
```java
package com.genderreveal.api.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final MagicLinkService magicLinkService;

    public AuthController(MagicLinkService magicLinkService) {
        this.magicLinkService = magicLinkService;
    }

    @PostMapping("/magic-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestMagicLink(@Valid @RequestBody MagicLinkRequest request) {
        magicLinkService.request(request.email());
    }
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.auth.MagicLinkRequestTest"` 후 `./gradlew test`
예상: PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/auth/ api/src/test/java/com/genderreveal/api/auth/MagicLinkRequestTest.java
git commit -m "feat(api): 매직링크 요청 API(POST /api/auth/magic-link) 추가"
```

---

### Task 4: 세션 생성과 매직링크 콜백 (`GET /api/auth/callback`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerSession.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerSessionRepository.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerSessionService.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerSessionCookie.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/AuthController.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/auth/OwnerSessionServiceTest.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/auth/MagicLinkCallbackTest.java`

**Interfaces:**
- Consumes: `MagicLinkService.consume`, `TokenGenerator`, `TokenHasher`, `AppProperties`, `RecordingEmailSender`
- Produces: `OwnerSession`(getId/getOwnerEmail/getExpiresAt), `OwnerSessionRepository.findBySessionTokenHash(String)`, `OwnerSessionService.create(String email): String`(원본 토큰 반환, 이메일 정규화), `findValid(String rawToken): Optional<OwnerSession>`, `delete(String rawToken)`, `OwnerSessionCookie.NAME = "owner_session"`, `OwnerSessionCookie.issue(String rawToken, boolean secure): String`(Set-Cookie 값), `OwnerSessionCookie.clear(boolean secure): String`, `GET /api/auth/callback?token=` → 302

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/auth/OwnerSessionServiceTest.java`:
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
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
@Transactional
class OwnerSessionServiceTest {

    @Autowired
    private OwnerSessionService sessionService;

    @Autowired
    private OwnerSessionRepository sessionRepository;

    @Autowired
    private Clock clock;

    @Test
    void createdSessionIsFoundByRawTokenAndStoredOnlyAsHash() {
        String raw = sessionService.create(" Owner@Example.com ");

        assertThat(sessionService.findValid(raw)).get()
            .extracting(OwnerSession::getOwnerEmail).isEqualTo("owner@example.com");
        assertThat(sessionRepository.findBySessionTokenHash(raw)).isEmpty();
        assertThat(sessionRepository.findBySessionTokenHash(TokenHasher.sha256(raw))).isPresent();
    }

    @Test
    void sessionExpiresAfterSevenDays() {
        String raw = sessionService.create("owner@example.com");

        MutableTestClock testClock = (MutableTestClock) clock;
        testClock.advanceTo(testClock.instant().plus(8, ChronoUnit.DAYS));

        assertThat(sessionService.findValid(raw)).isEmpty();
    }

    @Test
    void deleteRemovesSession() {
        String raw = sessionService.create("owner@example.com");

        sessionService.delete(raw);

        assertThat(sessionService.findValid(raw)).isEmpty();
    }

    @Test
    void unknownOrBlankTokensAreInvalid() {
        assertThat(sessionService.findValid("nope")).isEmpty();
        assertThat(sessionService.findValid(null)).isEmpty();
        assertThat(sessionService.findValid("")).isEmpty();
    }
}
```

`gender-reveal/api/src/test/java/com/genderreveal/api/auth/MagicLinkCallbackTest.java`:
```java
package com.genderreveal.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genderreveal.api.config.MutableTestClock;
import com.genderreveal.api.config.MutableTestClockConfig;
import com.genderreveal.api.email.RecordingEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableTestClockConfig.class)
@Transactional
class MagicLinkCallbackTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emails;

    @Autowired
    private Clock clock;

    @BeforeEach
    void resetEmails() {
        emails.clear();
    }

    @Test
    void validTokenCreatesSessionCookieAndRedirectsToDashboard() throws Exception {
        String token = requestTokenFor("cb-a@example.com");

        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/callback").param("token", token))
            .andExpect(status().isFound())
            .andReturn().getResponse();

        assertThat(response.getHeader("Location")).isEqualTo("http://localhost:8080/dashboard");
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("owner_session=").contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void tokenIsSingleUse() throws Exception {
        String token = requestTokenFor("cb-b@example.com");

        mockMvc.perform(get("/api/auth/callback").param("token", token)).andExpect(status().isFound());
        MockHttpServletResponse second = mockMvc.perform(get("/api/auth/callback").param("token", token))
            .andExpect(status().isFound())
            .andReturn().getResponse();

        assertThat(second.getHeader("Location")).isEqualTo("http://localhost:8080/login?error=invalid");
        assertThat(second.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = requestTokenFor("cb-c@example.com");

        MutableTestClock testClock = (MutableTestClock) clock;
        testClock.advanceTo(testClock.instant().plus(16, ChronoUnit.MINUTES));

        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/callback").param("token", token))
            .andExpect(status().isFound())
            .andReturn().getResponse();

        assertThat(response.getHeader("Location")).isEqualTo("http://localhost:8080/login?error=invalid");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/callback").param("token", "garbage"))
            .andExpect(status().isFound())
            .andReturn().getResponse();

        assertThat(response.getHeader("Location")).isEqualTo("http://localhost:8080/login?error=invalid");
    }

    private String requestTokenFor(String email) throws Exception {
        mockMvc.perform(post("/api/auth/magic-link")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("email", email))))
            .andExpect(status().isAccepted());
        Matcher matcher = TOKEN.matcher(emails.sent().get(0).body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.auth.OwnerSessionServiceTest" --tests "com.genderreveal.api.auth.MagicLinkCallbackTest"`
예상: 컴파일 실패 (`OwnerSessionService` 등 없음)

- [ ] **Step 3: 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerSession.java`:
```java
package com.genderreveal.api.auth;

import com.genderreveal.api.common.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "owner_sessions")
public class OwnerSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "session_token_hash", nullable = false, unique = true)
    private String sessionTokenHash;

    @Column(name = "expires_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant createdAt;

    protected OwnerSession() {
        // JPA
    }

    public OwnerSession(String ownerEmail, String sessionTokenHash, Instant expiresAt, Instant createdAt) {
        this.ownerEmail = ownerEmail;
        this.sessionTokenHash = sessionTokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getSessionTokenHash() { return sessionTokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerSessionRepository.java`:
```java
package com.genderreveal.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerSessionRepository extends JpaRepository<OwnerSession, Long> {
    Optional<OwnerSession> findBySessionTokenHash(String sessionTokenHash);
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerSessionService.java`:
```java
package com.genderreveal.api.auth;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class OwnerSessionService {

    static final Duration SESSION_TTL = Duration.ofDays(7);

    private final OwnerSessionRepository sessionRepository;
    private final Clock clock;

    public OwnerSessionService(OwnerSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    /** Creates a session for the (normalized) email and returns the raw token to put in the cookie. */
    public String create(String email) {
        Instant now = Instant.now(clock);
        String rawToken = TokenGenerator.newToken();
        sessionRepository.save(new OwnerSession(
            MagicLinkService.normalize(email), TokenHasher.sha256(rawToken), now.plus(SESSION_TTL), now));
        return rawToken;
    }

    public Optional<OwnerSession> findValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        return sessionRepository.findBySessionTokenHash(TokenHasher.sha256(rawToken))
            .filter(session -> session.getExpiresAt().isAfter(now));
    }

    public void delete(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessionRepository.findBySessionTokenHash(TokenHasher.sha256(rawToken)).ifPresent(sessionRepository::delete);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerSessionCookie.java`:
```java
package com.genderreveal.api.auth;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class OwnerSessionCookie {

    public static final String NAME = "owner_session";

    private OwnerSessionCookie() {}

    public static String issue(String rawToken, boolean secure) {
        return ResponseCookie.from(NAME, rawToken)
            .path("/")
            .maxAge(OwnerSessionService.SESSION_TTL)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .build()
            .toString();
    }

    public static String clear(boolean secure) {
        return ResponseCookie.from(NAME, "")
            .path("/")
            .maxAge(Duration.ZERO)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .build()
            .toString();
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/AuthController.java` 전체를 교체:
```java
package com.genderreveal.api.auth;

import com.genderreveal.api.config.AppProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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
    public void requestMagicLink(@Valid @RequestBody MagicLinkRequest request) {
        magicLinkService.request(request.email());
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

    private ResponseEntity.BodyBuilder redirect(String path) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appProperties.baseUrl() + path));
    }
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/auth/ api/src/test/java/com/genderreveal/api/auth/OwnerSessionServiceTest.java api/src/test/java/com/genderreveal/api/auth/MagicLinkCallbackTest.java
git commit -m "feat(api): 세션 생성과 매직링크 콜백(GET /api/auth/callback) 추가"
```

---

### Task 5: 소유자 인증 가드 + `POST /api/pages` 세션 필수화 + `me`/`logout`

이 태스크는 Plan 1의 생성 API를 깨는 변경(`ownerEmail` 제거)이라 기존 테스트 여러 개를 함께 고친다.

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerPrincipal.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerAuthInterceptor.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerPrincipalArgumentResolver.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/WebConfig.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/auth/AuthController.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageCreateRequest.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageController.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/auth/OwnerTestSupport.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/auth/OwnerAuthTest.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/page/PageControllerCreateTest.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/page/PageControllerGetTest.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/page/PageControllerExpiredTest.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/page/PageServiceRaceTest.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/guess/GuessControllerTest.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java`

**Interfaces:**
- Consumes: `OwnerSessionService.findValid/delete/create`, `OwnerSessionCookie`, `AppProperties`
- Produces: `record OwnerPrincipal(String email)`(컨트롤러 파라미터로 주입), 인터셉터가 지키는 경로(`POST /api/pages`, `/api/auth/me`, `/api/owner/**`), `GET /api/auth/me` → `{"email": "..."}`, `POST /api/auth/logout` → 204(쿠키 삭제), `PageService.create(PageCreateRequest, String ownerEmail): Page`, 테스트 빈 `OwnerTestSupport.cookieFor(String email): MockCookie`

- [ ] **Step 1: 테스트 지원 빈과 실패하는 인증 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/auth/OwnerTestSupport.java`:
```java
package com.genderreveal.api.auth;

import org.springframework.mock.web.MockCookie;
import org.springframework.stereotype.Component;

/** Test-only helper: logs an owner in directly (no email round-trip) and returns the session cookie. */
@Component
public class OwnerTestSupport {

    private final OwnerSessionService sessionService;

    public OwnerTestSupport(OwnerSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public MockCookie cookieFor(String email) {
        return new MockCookie(OwnerSessionCookie.NAME, sessionService.create(email));
    }
}
```

`gender-reveal/api/src/test/java/com/genderreveal/api/auth/OwnerAuthTest.java`:
```java
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
```

`gender-reveal/api/src/test/java/com/genderreveal/api/page/PageControllerCreateTest.java`에 테스트 3개를 추가한다 (클래스에 `@Autowired private OwnerTestSupport ownerTestSupport;`와 `@Autowired private PageRepository pageRepository;` 필드, `import com.genderreveal.api.auth.OwnerTestSupport;`, `import static org.assertj.core.api.Assertions.assertThat;`이 없으면 추가):
```java
    @Test
    void createWithoutSessionIs401() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true
        );

        mockMvc.perform(post("/api/pages")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerEmailComesFromSession() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "slug", "session-owner-slug"
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("Real-Owner@Example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        assertThat(pageRepository.findBySlug("session-owner-slug").orElseThrow().getOwnerEmail())
            .isEqualTo("real-owner@example.com");
    }

    @Test
    void ownerEmailInRequestBodyIsIgnored() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "slug", "spoof-attempt-slug",
            "ownerEmail", "victim@example.com"
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("attacker@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        assertThat(pageRepository.findBySlug("spoof-attempt-slug").orElseThrow().getOwnerEmail())
            .isEqualTo("attacker@example.com");
    }
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.auth.OwnerAuthTest"`
예상: 실패 (`/api/auth/me` 없음)

- [ ] **Step 3: 인증 가드 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerPrincipal.java`:
```java
package com.genderreveal.api.auth;

public record OwnerPrincipal(String email) {
    static final String REQUEST_ATTRIBUTE = OwnerPrincipal.class.getName();
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerAuthInterceptor.java`:
```java
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
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/OwnerPrincipalArgumentResolver.java`:
```java
package com.genderreveal.api.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class OwnerPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return OwnerPrincipal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object principal = webRequest.getAttribute(OwnerPrincipal.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (principal == null) {
            throw new IllegalStateException("OwnerPrincipal requested on a route not guarded by OwnerAuthInterceptor");
        }
        return principal;
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/WebConfig.java`:
```java
package com.genderreveal.api.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final OwnerAuthInterceptor ownerAuthInterceptor;
    private final OwnerPrincipalArgumentResolver ownerPrincipalArgumentResolver;

    public WebConfig(OwnerAuthInterceptor ownerAuthInterceptor,
                      OwnerPrincipalArgumentResolver ownerPrincipalArgumentResolver) {
        this.ownerAuthInterceptor = ownerAuthInterceptor;
        this.ownerPrincipalArgumentResolver = ownerPrincipalArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ownerAuthInterceptor)
            .addPathPatterns("/api/pages", "/api/auth/me", "/api/owner/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(ownerPrincipalArgumentResolver);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/auth/AuthController.java`에 다음 두 메서드를 추가한다 (필요한 import: `org.springframework.web.bind.annotation.CookieValue`, `java.util.Map`):
```java
    @GetMapping("/me")
    public Map<String, String> me(OwnerPrincipal owner) {
        return Map.of("email", owner.email());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = OwnerSessionCookie.NAME, required = false) String sessionToken) {
        sessionService.delete(sessionToken);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, OwnerSessionCookie.clear(appProperties.sessionCookieSecure()))
            .build();
    }
```
`/api/auth/logout`은 인터셉터 대상이 아니므로 세션이 없어도 204를 준다(멱등).

- [ ] **Step 4: 생성 API를 세션 기반으로 변경**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageCreateRequest.java`에서 `ownerEmail` 컴포넌트와 `jakarta.validation.constraints.Email` import를 제거한다. 결과:
```java
package com.genderreveal.api.page;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.LocalDate;

public record PageCreateRequest(
    @NotBlank String nickname,
    @NotNull @Pattern(regexp = "boy|girl") String actualGender,
    @NotNull Instant revealAt,
    LocalDate dueDate,
    String message,
    @NotNull @Pattern(regexp = "box|cake|balloon") String theme,
    boolean bgmEnabled,
    @Pattern(regexp = "[a-z0-9-]{3,32}") String slug
) {}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageService.java`의 `create` 시그니처와 `Page` 생성부를 다음으로 바꾼다 (나머지는 그대로):
```java
    public Page create(PageCreateRequest request, String ownerEmail) {
        Instant now = Instant.now(clock);
        validateRevealAt(request.revealAt(), now);

        String slug = resolveSlug(request.slug());

        Page page = new Page(
            slug, request.nickname(), request.actualGender(), request.revealAt(),
            request.dueDate(), request.message(), request.theme(), request.bgmEnabled(),
            ownerEmail, now, now.plus(RETENTION_DAYS, ChronoUnit.DAYS)
        );
```

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageController.java`의 `create`를 다음으로 바꾼다 (`import com.genderreveal.api.auth.OwnerPrincipal;` 추가):
```java
    @PostMapping
    public ResponseEntity<PageCreateResponse> create(OwnerPrincipal owner,
                                                       @Valid @RequestBody PageCreateRequest request) {
        Page page = pageService.create(request, owner.email());
        PageCreateResponse response = PageCreateResponse.from(page);
        return ResponseEntity.created(URI.create("/api/pages/" + page.getSlug())).body(response);
    }
```

- [ ] **Step 5: 기존 테스트 수리 (기계적 규칙)**

`POST /api/pages`를 MockMvc로 호출하는 기존 테스트를 전부 다음 규칙으로 고친다.

1. 요청 본문 `Map.of(...)`에서 `"ownerEmail", "owner@example.com"` 항목을 삭제한다 (마지막 항목이면 앞 줄 끝의 쉼표도 함께 제거).
2. 클래스에 `@Autowired private OwnerTestSupport ownerTestSupport;`와 `import com.genderreveal.api.auth.OwnerTestSupport;`를 추가한다.
3. `mockMvc.perform(post("/api/pages")` 바로 다음 줄에 `.cookie(ownerTestSupport.cookieFor("owner@example.com"))`를 추가한다.

대상 (라인은 Plan 2 머지 시점 기준):
- `PageControllerCreateTest`: ownerEmail 항목 40, 59, 76, 93, 110행 / `post("/api/pages")` 43, 62, 79, 96, 114, 119행
- `PageControllerGetTest`: 86행 / 90행
- `PageControllerExpiredTest`: 77행 / 81행 (이 클래스는 `MutableTestClockConfig`를 쓰므로 세션 생성은 항상 시계를 옮기기 **전에** 이루어진다 — 헬퍼가 `createPage(...)`를 호출하는 순서를 바꾸지 말 것)
- `GuessControllerTest`: 128행 / 132행
- `GuestbookEntryControllerTest`: 103, 134행 / 106, 138행

`PageServiceRaceTest`는 `PageCreateRequest` 생성자에서 `"owner@example.com"` 인자(여덟 번째)를 제거하고 호출을 `pageService.create(request, "owner@example.com")`로 바꾼다:
```java
        PageCreateRequest request = new PageCreateRequest(
            "뽀튼이", "boy", now.plus(1, ChronoUnit.DAYS), null, "메시지", "box", false,
            "race-slug");

        assertThatThrownBy(() -> pageService.create(request, "owner@example.com"))
            .isInstanceOf(SlugAlreadyTakenException.class);
```
엔티티를 직접 `new Page(..., "owner@example.com", ...)`으로 만드는 테스트(`*RepositoryTest`, `GuessServiceRaceTest`, `PageStatusCalculatorTest`)는 수정하지 않는다.

- [ ] **Step 6: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew clean test`
예상: 전체 PASS. 기존 테스트 개수 + 이번 태스크 신규 테스트만큼 늘어나야 하며 실패/스킵은 0이어야 한다.

- [ ] **Step 7: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/auth/ api/src/main/java/com/genderreveal/api/page/ api/src/test/java/
git commit -m "feat(api): 소유자 인증 가드 도입, 페이지 생성은 세션 필수(ownerEmail은 세션에서)"
```

---

### Task 6: 페이지 생성 시 발행 링크 이메일 발송

**Files:**
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageService.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/page/PageControllerCreateTest.java`

**Interfaces:**
- Consumes: `EmailSender`, `AppProperties`, `RecordingEmailSender`, `OwnerTestSupport`
- Produces: 페이지 생성 성공 후 소유자 이메일로 `{baseUrl}/g/{slug}` 링크 메일 1통 (발송 실패는 생성을 막지 않음)

- [ ] **Step 1: 실패하는 테스트 작성**

`PageControllerCreateTest`에 필드 `@Autowired private RecordingEmailSender emails;`(`import com.genderreveal.api.email.RecordingEmailSender;`)와 `@BeforeEach void resetEmails() { emails.clear(); }`(`import org.junit.jupiter.api.BeforeEach;`)를 추가하고 테스트를 추가한다:
```java
    @Test
    void sendsPublishedLinkToOwnerEmail() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "slug", "publish-mail-slug"
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("mail-owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        assertThat(emails.sent()).hasSize(1);
        RecordingEmailSender.SentEmail mail = emails.sent().get(0);
        assertThat(mail.to()).isEqualTo("mail-owner@example.com");
        assertThat(mail.body()).contains("http://localhost:8080/g/publish-mail-slug");
    }

    @Test
    void emailFailureDoesNotFailPageCreation() throws Exception {
        org.mockito.Mockito.doThrow(new com.genderreveal.api.email.EmailSendException("boom"))
            .when(emailSenderSpy).send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "slug", "mail-fails-slug"
        );

        mockMvc.perform(post("/api/pages")
                .cookie(ownerTestSupport.cookieFor("owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());
    }
```
두 번째 테스트를 위해 클래스에 `@org.springframework.boot.test.mock.mockito.SpyBean private RecordingEmailSender emailSenderSpy;`를 **`RecordingEmailSender emails` 필드 대신** 사용한다(같은 빈이므로 `emails`도 이 스파이를 가리키게 하여 필드 하나로 통일: `@SpyBean private RecordingEmailSender emails;`, 위 테스트의 `emailSenderSpy`를 `emails`로 바꾼다). Mockito 스텁은 테스트마다 초기화되지만 `clear()`는 `@BeforeEach`에서 그대로 호출한다.

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.PageControllerCreateTest"`
예상: `sendsPublishedLinkToOwnerEmail` 실패 (메일 0통)

- [ ] **Step 3: 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageService.java`를 수정한다. import 추가:
```java
import com.genderreveal.api.config.AppProperties;
import com.genderreveal.api.email.EmailSendException;
import com.genderreveal.api.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```
필드/생성자를 다음으로 바꾼다:
```java
    private static final Logger log = LoggerFactory.getLogger(PageService.class);

    private final PageRepository pageRepository;
    private final UniqueSlugAllocator slugAllocator;
    private final PageStatusCalculator statusCalculator;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final Clock clock;

    public PageService(PageRepository pageRepository, UniqueSlugAllocator slugAllocator,
                        PageStatusCalculator statusCalculator, EmailSender emailSender,
                        AppProperties appProperties, Clock clock) {
        this.pageRepository = pageRepository;
        this.slugAllocator = slugAllocator;
        this.statusCalculator = statusCalculator;
        this.emailSender = emailSender;
        this.appProperties = appProperties;
        this.clock = clock;
    }
```
`create`의 `try { return pageRepository.save(page); } catch ...` 블록을 다음으로 바꾼다 (catch 블록의 기존 주석/`throw new SlugAlreadyTakenException(slug)`는 그대로 유지):
```java
        Page saved;
        try {
            saved = pageRepository.save(page);
        } catch (DataAccessException ex) {
            // (기존 주석 유지)
            throw new SlugAlreadyTakenException(slug);
        }

        sendPublishedLink(saved);
        return saved;
```
그리고 클래스에 메서드를 추가한다:
```java
    private void sendPublishedLink(Page page) {
        String link = appProperties.baseUrl() + "/g/" + page.getSlug();
        String body = "페이지가 만들어졌어요. 아래 링크를 복사해 가족과 친구들에게 공유해 보세요.\n\n" + link;
        try {
            emailSender.send(page.getOwnerEmail(), "젠더리빌 페이지가 발행됐어요", body);
        } catch (EmailSendException ex) {
            log.error("Failed to send published-link email for slug {}", page.getSlug(), ex);
        }
    }
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/page/PageService.java api/src/test/java/com/genderreveal/api/page/PageControllerCreateTest.java
git commit -m "feat(api): 페이지 생성 시 소유자에게 발행 링크 이메일 발송"
```

---

### Task 7: 방문자 수 집계 (`GET /api/pages/{slug}`가 방문 기록 + 쿠키 발급)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/common/GuestCookie.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/visit/PageVisit.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/visit/PageVisitRepository.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/visit/VisitService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessController.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageController.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/visit/PageVisitTest.java`

**Interfaces:**
- Produces: `GuestCookie.NAME = "guest_id"`, `GuestCookie.resolve(String existing): String`(유효 UUID면 그대로, 아니면 새 UUID), `GuestCookie.isValid(String): boolean`, `GuestCookie.toSetCookie(String guestId): String`, `PageVisitRepository.existsByPageIdAndGuestCookieId(Long, String)`, `countByPageId(Long): long`, `VisitService.record(String slug, String guestId)`(멱등)

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/visit/PageVisitTest.java`:
```java
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
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.visit.PageVisitTest"`
예상: 컴파일 실패 (`PageVisitRepository` 없음)

- [ ] **Step 3: 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/common/GuestCookie.java`:
```java
package com.genderreveal.api.common;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** Anonymous guest identity: a random UUID in an httpOnly cookie. Not a login. */
@Component
public class GuestCookie {

    public static final String NAME = "guest_id";

    public boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String resolve(String existing) {
        return isValid(existing) ? existing : UUID.randomUUID().toString();
    }

    public String toSetCookie(String guestId) {
        return ResponseCookie.from(NAME, guestId)
            .path("/")
            .maxAge(Duration.ofDays(365))
            .httpOnly(true)
            .sameSite("Lax")
            .build()
            .toString();
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/visit/PageVisit.java`:
```java
package com.genderreveal.api.visit;

import com.genderreveal.api.common.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "page_visits")
public class PageVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(name = "guest_cookie_id", nullable = false)
    private String guestCookieId;

    @Column(name = "created_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant createdAt;

    protected PageVisit() {
        // JPA
    }

    public PageVisit(Long pageId, String guestCookieId, Instant createdAt) {
        this.pageId = pageId;
        this.guestCookieId = guestCookieId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getPageId() { return pageId; }
    public String getGuestCookieId() { return guestCookieId; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/visit/PageVisitRepository.java`:
```java
package com.genderreveal.api.visit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PageVisitRepository extends JpaRepository<PageVisit, Long> {
    boolean existsByPageIdAndGuestCookieId(Long pageId, String guestCookieId);

    long countByPageId(Long pageId);
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/visit/VisitService.java`:
```java
package com.genderreveal.api.visit;

import com.genderreveal.api.page.PageNotFoundException;
import com.genderreveal.api.page.PageRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class VisitService {

    private final PageVisitRepository visitRepository;
    private final PageRepository pageRepository;
    private final Clock clock;

    public VisitService(PageVisitRepository visitRepository, PageRepository pageRepository, Clock clock) {
        this.visitRepository = visitRepository;
        this.pageRepository = pageRepository;
        this.clock = clock;
    }

    /** Records one visit per (page, guest). Idempotent; a lost insert race is treated as "already recorded". */
    public void record(String slug, String guestId) {
        Long pageId = pageRepository.findBySlug(slug)
            .orElseThrow(() -> new PageNotFoundException(slug)).getId();
        if (visitRepository.existsByPageIdAndGuestCookieId(pageId, guestId)) {
            return;
        }
        try {
            visitRepository.save(new PageVisit(pageId, guestId, Instant.now(clock)));
        } catch (DataAccessException ex) {
            // Another request recorded the same (page_id, guest_cookie_id) first; UNIQUE guarantees exactly one row.
        }
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageController.java`를 수정한다. import 추가: `com.genderreveal.api.common.GuestCookie`, `com.genderreveal.api.visit.VisitService`, `org.springframework.http.HttpHeaders`, `org.springframework.web.bind.annotation.CookieValue`. 필드/생성자와 `getBySlug`를 다음으로 바꾼다:
```java
    private final PageService pageService;
    private final GuestCookie guestCookie;
    private final VisitService visitService;

    public PageController(PageService pageService, GuestCookie guestCookie, VisitService visitService) {
        this.pageService = pageService;
        this.guestCookie = guestCookie;
        this.visitService = visitService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PagePublicResponse> getBySlug(
            @PathVariable String slug,
            @CookieValue(name = GuestCookie.NAME, required = false) String existingGuestId) {
        PagePublicResponse response = pageService.getPublicView(slug);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if ("open".equals(response.status())) {
            String guestId = guestCookie.resolve(existingGuestId);
            visitService.record(slug, guestId);
            builder.header(HttpHeaders.SET_COOKIE, guestCookie.toSetCookie(guestId));
        }
        return builder.body(response);
    }
```

`gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessController.java`를 `GuestCookie`를 쓰도록 정리한다: `COOKIE_NAME`/`isValidUuid`/직접 만든 `ResponseCookie`를 제거하고 생성자에 `GuestCookie guestCookie`를 주입해 다음과 같이 쓴다 (동작은 동일, 사용하지 않게 된 import는 제거):
```java
    @PostMapping
    public ResponseEntity<GuessResponse> create(
            @PathVariable String slug,
            @CookieValue(name = GuestCookie.NAME, required = false) String existingGuestId,
            @Valid @RequestBody GuessCreateRequest request) {

        String guestId = guestCookie.resolve(existingGuestId);

        Guess guess = guessService.create(slug, guestId, request.guessedGender());

        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, guestCookie.toSetCookie(guestId))
            .body(GuessResponse.from(guess));
    }
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS (`GuessControllerTest`의 쿠키 관련 테스트가 리팩터링 후에도 통과)

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/common/ api/src/main/java/com/genderreveal/api/visit/ api/src/main/java/com/genderreveal/api/page/PageController.java api/src/main/java/com/genderreveal/api/guess/GuessController.java api/src/test/java/com/genderreveal/api/visit/
git commit -m "feat(api): 방문자 수 집계 — open 페이지 조회 시 guest_id 쿠키 발급과 방문 기록"
```

---

### Task 8: 방명록 작성 시 게스트 쿠키 저장

**Files:**
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntry.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryController.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java`

**Interfaces:**
- Produces: `GuestbookEntry(Long pageId, String nickname, String message, String guestCookieId, Instant createdAt)`(기존 4인자 생성자는 `guestCookieId = null`로 유지), `GuestbookEntry.getGuestCookieId(): String`(nullable), `GuestbookEntryService.create(String slug, String nickname, String message, String guestCookieId)`

- [ ] **Step 1: 실패하는 테스트 작성**

`GuestbookEntryControllerTest`에 테스트를 추가한다 (`import org.springframework.mock.web.MockCookie;`, `import java.util.UUID;`, `import static org.assertj.core.api.Assertions.assertThat;`가 없으면 추가):
```java
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
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guestbook.GuestbookEntryControllerTest"`
예상: 컴파일 실패 (`getGuestCookieId` 없음)

- [ ] **Step 3: 구현**

`GuestbookEntry.java`에 컬럼과 생성자, getter를 추가한다:
```java
    @Column(name = "guest_cookie_id")
    private String guestCookieId;
```
기존 4인자 생성자를 새 5인자 생성자에 위임하도록 바꾼다:
```java
    public GuestbookEntry(Long pageId, String nickname, String message, Instant createdAt) {
        this(pageId, nickname, message, null, createdAt);
    }

    public GuestbookEntry(Long pageId, String nickname, String message, String guestCookieId, Instant createdAt) {
        this.pageId = pageId;
        this.nickname = nickname;
        this.message = message;
        this.guestCookieId = guestCookieId;
        this.hidden = false;
        this.createdAt = createdAt;
    }
```
```java
    public String getGuestCookieId() { return guestCookieId; }
```

`GuestbookEntryService.create`를 다음으로 바꾼다:
```java
    public GuestbookEntry create(String slug, String nickname, String message, String guestCookieId) {
        Page page = requireOpenPage(slug);
        GuestbookEntry entry = new GuestbookEntry(page.getId(), nickname, message, guestCookieId, Instant.now(clock));
        return guestbookEntryRepository.save(entry);
    }
```

`GuestbookEntryController`에 `GuestCookie`를 주입하고(`import com.genderreveal.api.common.GuestCookie;`, `import org.springframework.web.bind.annotation.CookieValue;`) `create`를 다음으로 바꾼다:
```java
    @PostMapping
    public ResponseEntity<GuestbookEntryResponse> create(
            @PathVariable String slug,
            @CookieValue(name = GuestCookie.NAME, required = false) String existingGuestId,
            @Valid @RequestBody GuestbookEntryCreateRequest request) {
        String guestId = guestCookie.isValid(existingGuestId) ? existingGuestId : null;
        GuestbookEntry entry = guestbookEntryService.create(slug, request.nickname(), request.message(), guestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(GuestbookEntryResponse.from(entry));
    }
```
생성자는 `GuestbookEntryService`와 `GuestCookie` 두 개를 받도록 바꾼다.

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/guestbook/ api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java
git commit -m "feat(api): 방명록 작성 시 게스트 쿠키 저장(맞추기 표시용)"
```

---

### Task 9: 소유자 페이지 목록과 통계 (`GET /api/owner/pages`, `.../{slug}/stats`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageSummary.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/PageStats.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageService.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageController.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageRepository.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessRepository.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/owner/OwnerPageControllerTest.java`

**Interfaces:**
- Consumes: `OwnerPrincipal`, `PageRepository`, `PageVisitRepository.countByPageId`, `GuessRepository`, `PageStatusCalculator`
- Produces: `PageRepository.findByOwnerEmailOrderByCreatedAtDesc(String): List<Page>`, `GuessRepository.countByPageId(Long): long`, `countByPageIdAndGuessedGender(Long, String): long`, `record OwnerPageSummary(String slug, String nickname, String status, Instant revealAt, Instant expiresAt, boolean extended, String theme)`, `record PageStats(long visitors, long guessers, long boyGuesses, long girlGuesses)`, `OwnerPageService.requireOwned(String slug, String ownerEmail): Page`(없거나 남의 것이면 `PageNotFoundException`), `list(String ownerEmail): List<OwnerPageSummary>`, `stats(String slug, String ownerEmail): PageStats`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/owner/OwnerPageControllerTest.java`:
```java
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
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.owner.OwnerPageControllerTest"`
예상: 컴파일 실패

- [ ] **Step 3: 구현**

`PageRepository.java`에 추가 (`import java.util.List;`):
```java
    List<Page> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);
```

`GuessRepository.java`에 추가:
```java
    long countByPageId(Long pageId);

    long countByPageIdAndGuessedGender(Long pageId, String guessedGender);
```

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageSummary.java`:
```java
package com.genderreveal.api.owner;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageStatus;

import java.time.Instant;
import java.util.Locale;

public record OwnerPageSummary(String slug, String nickname, String status, Instant revealAt,
                                Instant expiresAt, boolean extended, String theme) {

    static OwnerPageSummary of(Page page, PageStatus status) {
        return new OwnerPageSummary(page.getSlug(), page.getNickname(), status.name().toLowerCase(Locale.ROOT),
            page.getRevealAt(), page.getExpiresAt(), page.isExtended(), page.getTheme());
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/PageStats.java`:
```java
package com.genderreveal.api.owner;

public record PageStats(long visitors, long guessers, long boyGuesses, long girlGuesses) {}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageService.java`:
```java
package com.genderreveal.api.owner;

import com.genderreveal.api.guess.GuessRepository;
import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageNotFoundException;
import com.genderreveal.api.page.PageRepository;
import com.genderreveal.api.page.PageStatusCalculator;
import com.genderreveal.api.visit.PageVisitRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class OwnerPageService {

    private final PageRepository pageRepository;
    private final PageVisitRepository visitRepository;
    private final GuessRepository guessRepository;
    private final PageStatusCalculator statusCalculator;
    private final Clock clock;

    public OwnerPageService(PageRepository pageRepository, PageVisitRepository visitRepository,
                             GuessRepository guessRepository, PageStatusCalculator statusCalculator, Clock clock) {
        this.pageRepository = pageRepository;
        this.visitRepository = visitRepository;
        this.guessRepository = guessRepository;
        this.statusCalculator = statusCalculator;
        this.clock = clock;
    }

    /** Missing and not-yours are indistinguishable on purpose (404, no existence leak). */
    public Page requireOwned(String slug, String ownerEmail) {
        return pageRepository.findBySlug(slug)
            .filter(page -> page.getOwnerEmail().equalsIgnoreCase(ownerEmail))
            .orElseThrow(() -> new PageNotFoundException(slug));
    }

    public List<OwnerPageSummary> list(String ownerEmail) {
        Instant now = Instant.now(clock);
        return pageRepository.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail).stream()
            .map(page -> OwnerPageSummary.of(page, statusCalculator.calculate(page, now)))
            .toList();
    }

    public PageStats stats(String slug, String ownerEmail) {
        Long pageId = requireOwned(slug, ownerEmail).getId();
        return new PageStats(
            visitRepository.countByPageId(pageId),
            guessRepository.countByPageId(pageId),
            guessRepository.countByPageIdAndGuessedGender(pageId, "boy"),
            guessRepository.countByPageIdAndGuessedGender(pageId, "girl"));
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageController.java`:
```java
package com.genderreveal.api.owner;

import com.genderreveal.api.auth.OwnerPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner/pages")
public class OwnerPageController {

    private final OwnerPageService ownerPageService;

    public OwnerPageController(OwnerPageService ownerPageService) {
        this.ownerPageService = ownerPageService;
    }

    @GetMapping
    public ResponseEntity<List<OwnerPageSummary>> list(OwnerPrincipal owner) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ownerPageService.list(owner.email()));
    }

    @GetMapping("/{slug}/stats")
    public ResponseEntity<PageStats> stats(OwnerPrincipal owner, @PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ownerPageService.stats(slug, owner.email()));
    }
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS (`PageNotFoundException`은 기존 `PageExceptionHandler`가 404로 처리)

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/owner/ api/src/main/java/com/genderreveal/api/page/PageRepository.java api/src/main/java/com/genderreveal/api/guess/GuessRepository.java api/src/test/java/com/genderreveal/api/owner/
git commit -m "feat(api): 소유자 페이지 목록과 통계 API 추가"
```

---

### Task 10: 소유자용 방명록 조회 (맞추기 결과 표시)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerGuestbookEntryResponse.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerGuestbookService.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerGuestbookController.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryRepository.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessRepository.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/owner/OwnerGuestbookControllerTest.java`

**Interfaces:**
- Consumes: `OwnerPageService.requireOwned`, `GuestbookEntry.getGuestCookieId`(Task 8), `Guess`
- Produces: `GuestbookEntryRepository.findByPageIdOrderByCreatedAtDesc(Long): List<GuestbookEntry>`, `GuessRepository.findByPageId(Long): List<Guess>`, `record OwnerGuestbookEntryResponse(Long id, String nickname, String message, Instant createdAt, boolean hidden, String guessedGender, Boolean guessCorrect)`(`guessedGender`/`guessCorrect`는 예측 기록이 없으면 null), `GET /api/owner/pages/{slug}/guestbook` — 숨김 포함 전체, 페이지 상태(secret/open/expired)와 무관하게 조회 가능

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/owner/OwnerGuestbookControllerTest.java`:
```java
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
            .andExpect(jsonPath("$[1].guessedGender").doesNotExist())
            .andExpect(jsonPath("$[1].guessCorrect").doesNotExist())
            .andExpect(jsonPath("$[2].nickname").value("쿠키없음"))
            .andExpect(jsonPath("$[2].guessedGender").doesNotExist())
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
```
JSON은 `null` 필드를 포함하므로(`spring.jackson.default-property-inclusion` 기본값) `doesNotExist()`가 통과하려면 응답 record에 `@JsonInclude(JsonInclude.Include.NON_NULL)`을 붙여 null 필드를 생략한다(Step 3).

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.owner.OwnerGuestbookControllerTest"`
예상: 컴파일 실패

- [ ] **Step 3: 구현**

`GuestbookEntryRepository.java`에 추가:
```java
    List<GuestbookEntry> findByPageIdOrderByCreatedAtDesc(Long pageId);
```
`GuessRepository.java`에 추가 (`import java.util.List;`):
```java
    List<Guess> findByPageId(Long pageId);
```

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerGuestbookEntryResponse.java`:
```java
package com.genderreveal.api.owner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.genderreveal.api.guess.Guess;
import com.genderreveal.api.guestbook.GuestbookEntry;
import com.genderreveal.api.page.Page;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OwnerGuestbookEntryResponse(Long id, String nickname, String message, Instant createdAt,
                                           boolean hidden, String guessedGender, Boolean guessCorrect) {

    static OwnerGuestbookEntryResponse of(GuestbookEntry entry, Page page, Guess guess) {
        String guessedGender = guess == null ? null : guess.getGuessedGender();
        Boolean correct = guess == null ? null : guess.getGuessedGender().equals(page.getActualGender());
        return new OwnerGuestbookEntryResponse(entry.getId(), entry.getNickname(), entry.getMessage(),
            entry.getCreatedAt(), entry.isHidden(), guessedGender, correct);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerGuestbookService.java`:
```java
package com.genderreveal.api.owner;

import com.genderreveal.api.guess.Guess;
import com.genderreveal.api.guess.GuessRepository;
import com.genderreveal.api.guestbook.GuestbookEntryRepository;
import com.genderreveal.api.page.Page;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OwnerGuestbookService {

    private final OwnerPageService ownerPageService;
    private final GuestbookEntryRepository guestbookEntryRepository;
    private final GuessRepository guessRepository;

    public OwnerGuestbookService(OwnerPageService ownerPageService, GuestbookEntryRepository guestbookEntryRepository,
                                  GuessRepository guessRepository) {
        this.ownerPageService = ownerPageService;
        this.guestbookEntryRepository = guestbookEntryRepository;
        this.guessRepository = guessRepository;
    }

    public List<OwnerGuestbookEntryResponse> list(String slug, String ownerEmail) {
        Page page = ownerPageService.requireOwned(slug, ownerEmail);

        Map<String, Guess> guessesByGuest = new HashMap<>();
        for (Guess guess : guessRepository.findByPageId(page.getId())) {
            guessesByGuest.put(guess.getGuestCookieId(), guess);
        }

        return guestbookEntryRepository.findByPageIdOrderByCreatedAtDesc(page.getId()).stream()
            .map(entry -> OwnerGuestbookEntryResponse.of(
                entry, page, entry.getGuestCookieId() == null ? null : guessesByGuest.get(entry.getGuestCookieId())))
            .toList();
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerGuestbookController.java`:
```java
package com.genderreveal.api.owner;

import com.genderreveal.api.auth.OwnerPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner/pages/{slug}/guestbook")
public class OwnerGuestbookController {

    private final OwnerGuestbookService ownerGuestbookService;

    public OwnerGuestbookController(OwnerGuestbookService ownerGuestbookService) {
        this.ownerGuestbookService = ownerGuestbookService;
    }

    @GetMapping
    public ResponseEntity<List<OwnerGuestbookEntryResponse>> list(OwnerPrincipal owner, @PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ownerGuestbookService.list(slug, owner.email()));
    }
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/owner/ api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryRepository.java api/src/main/java/com/genderreveal/api/guess/GuessRepository.java api/src/test/java/com/genderreveal/api/owner/OwnerGuestbookControllerTest.java
git commit -m "feat(api): 소유자용 방명록 조회 — 숨김 포함, 작성자 맞추기 결과 표시"
```

---

### Task 11: 방명록 숨김/노출/삭제 (`PATCH`, `DELETE`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryNotFoundException.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/GuestbookHiddenRequest.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerExceptionHandler.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntry.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerGuestbookService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerGuestbookController.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/owner/OwnerGuestbookControllerTest.java`

**Interfaces:**
- Produces: `GuestbookEntry.show()`, `PATCH /api/owner/pages/{slug}/guestbook/{entryId}` (`{"hidden": true|false}`) → 204, `DELETE .../{entryId}` → 204, 없거나 다른 페이지의 항목이면 404, `OwnerExceptionHandler`(이 패키지의 전용 예외만 처리)

- [ ] **Step 1: 실패하는 테스트 작성**

`OwnerGuestbookControllerTest`에 필드 `@Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;`와 import(`static ...MockMvcRequestBuilders.patch`, `.delete`, `com.genderreveal.api.guestbook.GuestbookEntryRepository`는 이미 있음, `static org.assertj.core.api.Assertions.assertThat`, `java.util.Map`)를 추가하고 테스트를 추가한다:
```java
    @Test
    void hideThenShowTogglesPublicVisibility() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page page = openPage("mod-slug", "mod-owner@example.com", now);
        GuestbookEntry entry = guestbookEntryRepository.save(new GuestbookEntry(page.getId(), "이모", "축하", now));

        mockMvc.perform(patch("/api/owner/pages/mod-slug/guestbook/" + entry.getId())
                .cookie(ownerTestSupport.cookieFor("mod-owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("hidden", true))))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/pages/mod-slug/guestbook")).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(patch("/api/owner/pages/mod-slug/guestbook/" + entry.getId())
                .cookie(ownerTestSupport.cookieFor("mod-owner@example.com"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("hidden", false))))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/pages/mod-slug/guestbook")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void deleteRemovesTheEntry() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page page = openPage("del-slug", "del-owner@example.com", now);
        GuestbookEntry entry = guestbookEntryRepository.save(new GuestbookEntry(page.getId(), "스팸", "광고", now));

        mockMvc.perform(delete("/api/owner/pages/del-slug/guestbook/" + entry.getId())
                .cookie(ownerTestSupport.cookieFor("del-owner@example.com")))
            .andExpect(status().isNoContent());

        assertThat(guestbookEntryRepository.findById(entry.getId())).isEmpty();
    }

    @Test
    void entryOfAnotherPageOrOwnerIs404() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page mine = openPage("mine-slug", "mine-owner@example.com", now);
        Page other = openPage("other-slug", "other-owner@example.com", now);
        GuestbookEntry otherEntry = guestbookEntryRepository.save(new GuestbookEntry(other.getId(), "남", "남의 글", now));

        // Right owner, but the entry belongs to a different page.
        mockMvc.perform(delete("/api/owner/pages/mine-slug/guestbook/" + otherEntry.getId())
                .cookie(ownerTestSupport.cookieFor("mine-owner@example.com")))
            .andExpect(status().isNotFound());
        // Wrong owner.
        mockMvc.perform(delete("/api/owner/pages/other-slug/guestbook/" + otherEntry.getId())
                .cookie(ownerTestSupport.cookieFor("mine-owner@example.com")))
            .andExpect(status().isNotFound());
        // Unknown entry id.
        mockMvc.perform(delete("/api/owner/pages/mine-slug/guestbook/999999")
                .cookie(ownerTestSupport.cookieFor("mine-owner@example.com")))
            .andExpect(status().isNotFound());

        assertThat(guestbookEntryRepository.findById(otherEntry.getId())).isPresent();
        assertThat(mine.getId()).isNotNull();
    }

    private Page openPage(String slug, String ownerEmail, Instant now) {
        return pageRepository.save(new Page(
            slug, "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, ownerEmail, now, now.plus(30, ChronoUnit.DAYS)));
    }
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.owner.OwnerGuestbookControllerTest"`
예상: 컴파일 실패 또는 405

- [ ] **Step 3: 구현**

`GuestbookEntry.java`에 `hide()` 옆에 추가:
```java
    public void show() {
        this.hidden = false;
    }
```

`gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryNotFoundException.java`:
```java
package com.genderreveal.api.guestbook;

public class GuestbookEntryNotFoundException extends RuntimeException {
    public GuestbookEntryNotFoundException(Long entryId) {
        super("Guestbook entry not found: " + entryId);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/GuestbookHiddenRequest.java`:
```java
package com.genderreveal.api.owner;

import jakarta.validation.constraints.NotNull;

public record GuestbookHiddenRequest(@NotNull Boolean hidden) {}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerExceptionHandler.java`:
```java
package com.genderreveal.api.owner;

import com.genderreveal.api.guestbook.GuestbookEntryNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class OwnerExceptionHandler {

    @ExceptionHandler(GuestbookEntryNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntryNotFound(GuestbookEntryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
```

`OwnerGuestbookService`에 다음을 추가한다 (`import com.genderreveal.api.guestbook.GuestbookEntry;`, `import com.genderreveal.api.guestbook.GuestbookEntryNotFoundException;`):
```java
    public void setHidden(String slug, String ownerEmail, Long entryId, boolean hidden) {
        GuestbookEntry entry = requireEntry(slug, ownerEmail, entryId);
        if (hidden) {
            entry.hide();
        } else {
            entry.show();
        }
        guestbookEntryRepository.save(entry);
    }

    public void delete(String slug, String ownerEmail, Long entryId) {
        guestbookEntryRepository.delete(requireEntry(slug, ownerEmail, entryId));
    }

    private GuestbookEntry requireEntry(String slug, String ownerEmail, Long entryId) {
        Page page = ownerPageService.requireOwned(slug, ownerEmail);
        return guestbookEntryRepository.findById(entryId)
            .filter(entry -> entry.getPageId().equals(page.getId()))
            .orElseThrow(() -> new GuestbookEntryNotFoundException(entryId));
    }
```

`OwnerGuestbookController`에 추가한다 (import: `jakarta.validation.Valid`, `org.springframework.web.bind.annotation.DeleteMapping`, `PatchMapping`, `RequestBody`):
```java
    @PatchMapping("/{entryId}")
    public ResponseEntity<Void> setHidden(OwnerPrincipal owner, @PathVariable String slug,
                                           @PathVariable Long entryId,
                                           @Valid @RequestBody GuestbookHiddenRequest request) {
        ownerGuestbookService.setHidden(slug, owner.email(), entryId, request.hidden());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> delete(OwnerPrincipal owner, @PathVariable String slug, @PathVariable Long entryId) {
        ownerGuestbookService.delete(slug, owner.email(), entryId);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/owner/ api/src/main/java/com/genderreveal/api/guestbook/ api/src/test/java/com/genderreveal/api/owner/OwnerGuestbookControllerTest.java
git commit -m "feat(api): 소유자 방명록 숨김/노출/삭제 API 추가"
```

---

### Task 12: 보관주기 연장 (`POST /api/owner/pages/{slug}/extend`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/ExtensionAlreadyUsedException.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/Page.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageController.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerExceptionHandler.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/owner/OwnerPageControllerTest.java`

**Interfaces:**
- Produces: `Page.extend(Instant newExpiresAt)`(`expiresAt` 갱신 + `extended = true`), `OwnerPageService.extend(String slug, String ownerEmail): OwnerPageSummary`, `POST /api/owner/pages/{slug}/extend` → 200 + `OwnerPageSummary` | 404 | 409(`ExtensionAlreadyUsedException`)

- [ ] **Step 1: 실패하는 테스트 작성**

`OwnerPageControllerTest`에 import(`static ...MockMvcRequestBuilders.post`, `static org.assertj.core.api.Assertions.assertThat`, `static org.assertj.core.api.Assertions.within`, `java.time.Duration`)를 추가하고 테스트를 추가한다:
```java
    @Test
    void extendAddsThirtyDaysToTheCurrentExpiryOnceOnly() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page page = pageRepository.save(new Page(
            "ext-slug", "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "ext-owner@example.com", now, now.plus(5, ChronoUnit.DAYS)));

        mockMvc.perform(post("/api/owner/pages/ext-slug/extend")
                .cookie(ownerTestSupport.cookieFor("ext-owner@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.extended").value(true))
            .andExpect(jsonPath("$.status").value("open"));

        Page reloaded = pageRepository.findById(page.getId()).orElseThrow();
        assertThat(reloaded.isExtended()).isTrue();
        assertThat(reloaded.getExpiresAt()).isEqualTo(now.plus(35, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/owner/pages/ext-slug/extend")
                .cookie(ownerTestSupport.cookieFor("ext-owner@example.com")))
            .andExpect(status().isConflict());
    }

    @Test
    void extendingAnAlreadyExpiredPageRestartsThirtyDaysFromNow() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page page = pageRepository.save(new Page(
            "ext-expired-slug", "뽀튼이", "boy", now.minus(40, ChronoUnit.DAYS),
            null, "메시지", "box", false, "ext-owner@example.com",
            now.minus(35, ChronoUnit.DAYS), now.minus(5, ChronoUnit.DAYS)));

        mockMvc.perform(post("/api/owner/pages/ext-expired-slug/extend")
                .cookie(ownerTestSupport.cookieFor("ext-owner@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("open"));

        Page reloaded = pageRepository.findById(page.getId()).orElseThrow();
        assertThat(reloaded.getExpiresAt()).isCloseTo(now.plus(30, ChronoUnit.DAYS), within(Duration.ofSeconds(30)));
    }

    @Test
    void extendingSomeoneElsesPageIs404() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        pageRepository.save(new Page(
            "ext-private-slug", "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "victim@example.com", now, now.plus(5, ChronoUnit.DAYS)));

        mockMvc.perform(post("/api/owner/pages/ext-private-slug/extend")
                .cookie(ownerTestSupport.cookieFor("intruder@example.com")))
            .andExpect(status().isNotFound());
    }
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.owner.OwnerPageControllerTest"`
예상: 실패 (404/405 — 엔드포인트 없음)

- [ ] **Step 3: 구현**

`Page.java`에 추가:
```java
    public void extend(Instant newExpiresAt) {
        this.expiresAt = newExpiresAt;
        this.extended = true;
    }
```

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/ExtensionAlreadyUsedException.java`:
```java
package com.genderreveal.api.owner;

public class ExtensionAlreadyUsedException extends RuntimeException {
    public ExtensionAlreadyUsedException(String slug) {
        super("Retention extension already used for page: " + slug);
    }
}
```

`OwnerExceptionHandler`에 추가:
```java
    @ExceptionHandler(ExtensionAlreadyUsedException.class)
    public ResponseEntity<Map<String, String>> handleExtensionUsed(ExtensionAlreadyUsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
```

`OwnerPageService`에 추가 (`import java.time.temporal.ChronoUnit;`, `import com.genderreveal.api.page.PageStatus;`):
```java
    private static final int EXTENSION_DAYS = 30;

    public OwnerPageSummary extend(String slug, String ownerEmail) {
        Page page = requireOwned(slug, ownerEmail);
        if (page.isExtended()) {
            throw new ExtensionAlreadyUsedException(slug);
        }
        Instant now = Instant.now(clock);
        Instant base = page.getExpiresAt().isAfter(now) ? page.getExpiresAt() : now;
        page.extend(base.plus(EXTENSION_DAYS, ChronoUnit.DAYS));
        Page saved = pageRepository.save(page);
        PageStatus status = statusCalculator.calculate(saved, now);
        return OwnerPageSummary.of(saved, status);
    }
```

`OwnerPageController`에 추가 (`import org.springframework.web.bind.annotation.PostMapping;`):
```java
    @PostMapping("/{slug}/extend")
    public ResponseEntity<OwnerPageSummary> extend(OwnerPrincipal owner, @PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ownerPageService.extend(slug, owner.email()));
    }
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew clean test`
예상: 전체 PASS (실패/스킵 0)

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/owner/ api/src/main/java/com/genderreveal/api/page/Page.java api/src/test/java/com/genderreveal/api/owner/OwnerPageControllerTest.java
git commit -m "feat(api): 보관주기 1회 연장 API 추가"
```

---

## 이 계획 완료 후 상태

- 소유자는 이메일 매직링크로 로그인(=이메일 소유 검증)한 뒤에만 페이지를 만들 수 있고, 발행 링크가 본인 이메일로 온다. 방문자는 여전히 로그인 없이 익명 쿠키만 쓴다.
- 소유자는 자기 페이지 목록·통계(방문자/참여자/예측 비율)를 보고, 방명록을 숨김·노출·삭제하며(작성자의 맞추기 정답/오답 표시 포함), 보관주기를 1회 연장할 수 있다.
- 여전히 없는 것: 프론트엔드(Plan 4, nginx 포함), 관리자 대시보드/작성 폼 화면(Plan 5), Docker(Task 8 보류), 공개 쓰기 엔드포인트 레이트리밋·방명록 페이지네이션·검증 에러 JSON 통일·쿠키 `Secure` 기본값(하드닝).

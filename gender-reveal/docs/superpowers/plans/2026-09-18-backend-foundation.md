# 젠더리빌 백엔드 기반 구현 계획 (Plan 1: 저장소 스캐폴딩 + 백엔드 기반)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 모노레포 저장소 구조와 Spring Boot 백엔드의 기반(빌드, DB 스키마, 페이지 생성/조회 API, 상태 계산 로직)을 인증 없이 동작하는 상태로 구현한다.

**Architecture:** Java 21 + Gradle + Spring Boot 3, Spring Data JPA(Hibernate 커뮤니티 SQLite dialect)로 SQLite 파일 DB에 접근하고, Flyway로 전체 스키마를 한 번에 마이그레이션한다. `PageStatusCalculator`가 저장된 컬럼이 아니라 조회 시점에 `secret`/`open`/`expired` 상태를 계산한다.

**Tech Stack:** Java 21, Gradle(Kotlin DSL), Spring Boot 3.3.x, Spring Data JPA, Hibernate community dialects, Flyway, SQLite(xerial JDBC 드라이버), JUnit5, MockMvc, Mockito, AssertJ, Docker.

**Spec:** [docs/superpowers/specs/2026-09-18-implementation-design.md](../specs/2026-09-18-implementation-design.md)

## 범위 조정 안내

브레인스토밍 결과 안내에서는 이 계획에 nginx 설정도 포함한다고 했으나, nginx가 라우팅할 `web/`(Next.js)가 아직 존재하지 않아 지금 추가해도 검증할 방법이 없다. nginx 리버스 프록시 설정은 **Plan 4(프론트엔드 기반)**에서 `web/`와 함께 추가한다. 이 계획은 API를 호스트에 직접 노출(`localhost:8080`)한 상태로 검증한다.

## Global Constraints

- 언어/런타임: Java 21 (스펙 2절)
- 빌드 도구: Gradle, Kotlin DSL (스펙 2절)
- DB: SQLite, 임베디드 파일, 별도 DB 서버 프로세스 없음 (스펙 2절, 4절)
- ORM: Spring Data JPA + Hibernate 커뮤니티 SQLite dialect (스펙 2절)
- 마이그레이션: Flyway, SQL 파일 기반 (스펙 2절)
- API 스타일: REST, `/api` 하위 경로 (스펙 5절)
- 페이지 상태(`secret`/`open`/`expired`)는 컬럼 저장이 아니라 조회 시점 계산 (스펙 4절)
- 보관주기: 페이지 생성 시 `expires_at = created_at + 30일`로 계산해 저장 (스펙 4절)
- 패키지 루트: `com.genderreveal.api`

---

### Task 1: 모노레포 스캐폴딩 + Spring Boot 초기화 + 헬스체크

**Files:**
- Create: `gender-reveal/api/settings.gradle.kts`
- Create: `gender-reveal/api/build.gradle.kts`
- Create: `gender-reveal/api/src/main/resources/application.yml`
- Create: `gender-reveal/api/src/test/resources/application-test.yml`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/GenderRevealApiApplication.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/health/HealthController.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/health/HealthControllerTest.java`
- Modify: `gender-reveal/.gitignore`

**Interfaces:**
- Produces: `GET /api/health` → `{"status":"ok"}` (200)

- [ ] **Step 1: Gradle 프로젝트 골격 생성**

`gender-reveal/api/settings.gradle.kts`:
```kotlin
rootProject.name = "gender-reveal-api"
```

`gender-reveal/api/build.gradle.kts`:
```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.genderreveal"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.hibernate.orm:hibernate-community-dialects")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.xerial:sqlite-jdbc:3.46.1.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

`gender-reveal/api` 디렉터리에서 시스템 Gradle(8.10 이상)로 wrapper를 생성한다:
```bash
cd gender-reveal/api
gradle wrapper --gradle-version 8.10
```
이 명령은 `gradlew`, `gradlew.bat`, `gradle/wrapper/`를 생성한다. 이후 모든 빌드/테스트 명령은 `./gradlew`를 사용한다.

- [ ] **Step 2: 데이터소스 설정 작성**

`gender-reveal/api/src/main/resources/application.yml`:
```yaml
server:
  port: 8080

spring:
  application:
    name: gender-reveal-api
  datasource:
    url: jdbc:sqlite:${GENDER_REVEAL_DB_PATH:./data/gender-reveal.db}
    driver-class-name: org.sqlite.JDBC
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        dialect: org.hibernate.community.dialect.SQLiteDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
```

`gender-reveal/api/src/test/resources/application-test.yml` (테스트는 공유 캐시 인메모리 SQLite를 쓴다 — SQLite의 `:memory:`는 연결마다 별도 DB가 생기므로, `cache=shared` + 커넥션 풀 1개로 고정해 모든 테스트 커넥션이 같은 인메모리 DB를 보게 한다):
```yaml
spring:
  datasource:
    url: jdbc:sqlite:file::memory:?cache=shared
    driver-class-name: org.sqlite.JDBC
    hikari:
      maximum-pool-size: 1
  jpa:
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        dialect: org.hibernate.community.dialect.SQLiteDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
```

- [ ] **Step 3: 애플리케이션 진입점 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/GenderRevealApiApplication.java`:
```java
package com.genderreveal.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GenderRevealApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(GenderRevealApiApplication.class, args);
    }
}
```

- [ ] **Step 4: 실패하는 헬스체크 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/health/HealthControllerTest.java`:
```java
package com.genderreveal.api.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"ok\"}"));
    }
}
```

- [ ] **Step 5: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.health.HealthControllerTest"`
예상 결과: 컴파일 실패 또는 404 — `HealthController`가 아직 없음

- [ ] **Step 6: 헬스체크 컨트롤러 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/health/HealthController.java`:
```java
package com.genderreveal.api.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
```

- [ ] **Step 7: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.health.HealthControllerTest"`
예상 결과: PASS

- [ ] **Step 8: .gitignore에 빌드 산출물/로컬 DB 파일 추가**

`gender-reveal/.gitignore`에 추가:
```
gender-reveal/api/build/
gender-reveal/api/.gradle/
gender-reveal/api/data/
```

- [ ] **Step 9: 커밋**

```bash
cd gender-reveal
git add api/ .gitignore
git commit -m "feat(api): Spring Boot 프로젝트 초기화 + 헬스체크 API"
```

---

### Task 2: Flyway 마이그레이션 (전체 스키마)

**Files:**
- Create: `gender-reveal/api/src/main/resources/db/migration/V1__init.sql`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/migration/FlywayMigrationTest.java`

**Interfaces:**
- Produces: SQLite 테이블 `pages`, `guesses`, `guestbook_entries`, `magic_link_tokens`, `owner_sessions` (스펙 4절 스키마 그대로)

- [ ] **Step 1: 실패하는 마이그레이션 검증 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/migration/FlywayMigrationTest.java`:
```java
package com.genderreveal.api.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allExpectedTablesExist() {
        List<String> tableNames = jdbcTemplate.queryForList(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'flyway_schema_history'",
            String.class
        );

        assertThat(tableNames).containsExactlyInAnyOrder(
            "pages", "guesses", "guestbook_entries", "magic_link_tokens", "owner_sessions"
        );
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.migration.FlywayMigrationTest"`
예상 결과: FAIL — 테이블이 하나도 없음

- [ ] **Step 3: 마이그레이션 SQL 작성**

`gender-reveal/api/src/main/resources/db/migration/V1__init.sql`:
```sql
CREATE TABLE pages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    nickname TEXT NOT NULL,
    actual_gender TEXT NOT NULL CHECK (actual_gender IN ('boy', 'girl')),
    reveal_at TEXT NOT NULL,
    due_date TEXT,
    message TEXT,
    theme TEXT NOT NULL CHECK (theme IN ('box', 'cake', 'balloon')),
    bgm_enabled INTEGER NOT NULL DEFAULT 0,
    owner_email TEXT NOT NULL,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    extended INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE guesses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    page_id INTEGER NOT NULL REFERENCES pages(id),
    guest_cookie_id TEXT NOT NULL,
    guessed_gender TEXT NOT NULL CHECK (guessed_gender IN ('boy', 'girl')),
    created_at TEXT NOT NULL,
    UNIQUE (page_id, guest_cookie_id)
);

CREATE TABLE guestbook_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    page_id INTEGER NOT NULL REFERENCES pages(id),
    nickname TEXT NOT NULL,
    message TEXT NOT NULL,
    hidden INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

CREATE TABLE magic_link_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_email TEXT NOT NULL,
    page_id INTEGER REFERENCES pages(id),
    token_hash TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    used INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

CREATE TABLE owner_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    page_id INTEGER NOT NULL REFERENCES pages(id),
    session_token_hash TEXT NOT NULL UNIQUE,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_guesses_page_id ON guesses(page_id);
CREATE INDEX idx_guestbook_entries_page_id ON guestbook_entries(page_id);
CREATE INDEX idx_magic_link_tokens_token_hash ON magic_link_tokens(token_hash);
CREATE INDEX idx_owner_sessions_token_hash ON owner_sessions(session_token_hash);
```

**참고:** Flyway Community(`flyway-core`)는 `jdbc:sqlite:` URL을 자체적으로 인식해 별도 모듈 없이 동작한다(Flyway 9 이상). 만약 마이그레이션 실행 시 "지원하지 않는 데이터베이스" 오류가 나면 `build.gradle.kts`에 `implementation("org.flywaydb:flyway-database-sqlite")`를 추가한다 — Flyway가 일부 커뮤니티 DB 지원을 별도 모듈로 분리한 버전이 있기 때문이다.

- [ ] **Step 4: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.migration.FlywayMigrationTest"`
예상 결과: PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/resources/db/migration/ api/src/test/java/com/genderreveal/api/migration/
git commit -m "feat(api): Flyway 초기 스키마 마이그레이션 추가"
```

---

### Task 3: Page 엔티티 + Repository

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/Page.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageRepository.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/page/PageRepositoryTest.java`

**Interfaces:**
- Produces: `Page`(엔티티, getter: getId/getSlug/getNickname/getActualGender/getRevealAt/getDueDate/getMessage/getTheme/isBgmEnabled/getOwnerEmail/getCreatedAt/getExpiresAt/isExtended), `PageRepository.findBySlug(String): Optional<Page>`, `PageRepository.existsBySlug(String): boolean`

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/page/PageRepositoryTest.java`:
```java
package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PageRepositoryTest {

    @Autowired
    private PageRepository pageRepository;

    @Test
    void savesAndFindsBySlug() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Page page = new Page(
            "ppo-2026", "뽀튼이", "boy", now.plus(1, ChronoUnit.DAYS),
            null, "왕자님이 찾아왔어요!", "box", true,
            "owner@example.com", now, now.plus(30, ChronoUnit.DAYS)
        );

        pageRepository.save(page);

        Optional<Page> found = pageRepository.findBySlug("ppo-2026");

        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("뽀튼이");
        assertThat(found.get().getActualGender()).isEqualTo("boy");
    }

    @Test
    void existsBySlugReflectsSavedPages() {
        assertThat(pageRepository.existsBySlug("missing-slug")).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.PageRepositoryTest"`
예상 결과: 컴파일 실패 — `Page`, `PageRepository`가 아직 없음

- [ ] **Step 3: Page 엔티티 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/Page.java`:
```java
package com.genderreveal.api.page;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "actual_gender", nullable = false)
    private String actualGender;

    @Column(name = "reveal_at", nullable = false)
    private Instant revealAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    private String message;

    @Column(nullable = false)
    private String theme;

    @Column(name = "bgm_enabled", nullable = false)
    private boolean bgmEnabled;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean extended;

    protected Page() {
        // JPA
    }

    public Page(String slug, String nickname, String actualGender, Instant revealAt,
                LocalDate dueDate, String message, String theme, boolean bgmEnabled,
                String ownerEmail, Instant createdAt, Instant expiresAt) {
        this.slug = slug;
        this.nickname = nickname;
        this.actualGender = actualGender;
        this.revealAt = revealAt;
        this.dueDate = dueDate;
        this.message = message;
        this.theme = theme;
        this.bgmEnabled = bgmEnabled;
        this.ownerEmail = ownerEmail;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.extended = false;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getNickname() { return nickname; }
    public String getActualGender() { return actualGender; }
    public Instant getRevealAt() { return revealAt; }
    public LocalDate getDueDate() { return dueDate; }
    public String getMessage() { return message; }
    public String getTheme() { return theme; }
    public boolean isBgmEnabled() { return bgmEnabled; }
    public String getOwnerEmail() { return ownerEmail; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExtended() { return extended; }
}
```

- [ ] **Step 4: PageRepository 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageRepository.java`:
```java
package com.genderreveal.api.page;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {
    Optional<Page> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
```

- [ ] **Step 5: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.PageRepositoryTest"`
예상 결과: PASS

- [ ] **Step 6: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/page/Page.java api/src/main/java/com/genderreveal/api/page/PageRepository.java api/src/test/java/com/genderreveal/api/page/PageRepositoryTest.java
git commit -m "feat(api): Page 엔티티와 Repository 추가"
```

---

### Task 4: 슬러그 생성 및 유일성 할당

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/SlugGenerator.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/UniqueSlugAllocator.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/page/SlugGeneratorTest.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/page/UniqueSlugAllocatorTest.java`

**Interfaces:**
- Consumes: `PageRepository.existsBySlug(String): boolean` (Task 3)
- Produces: `SlugGenerator.generate(): String`, `UniqueSlugAllocator.allocate(): String`

- [ ] **Step 1: 실패하는 SlugGenerator 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/page/SlugGeneratorTest.java`:
```java
package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    private final SlugGenerator generator = new SlugGenerator();
    private static final Pattern VALID_SLUG = Pattern.compile("^[a-z0-9]{8}$");

    @Test
    void generatesEightCharacterLowercaseAlphanumericSlug() {
        String slug = generator.generate();

        assertThat(slug).matches(VALID_SLUG);
    }

    @Test
    void generatesDistinctSlugsAcrossManyCalls() {
        Set<String> slugs = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            slugs.add(generator.generate());
        }

        assertThat(slugs).hasSize(1000);
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.SlugGeneratorTest"`
예상 결과: 컴파일 실패 — `SlugGenerator`가 아직 없음

- [ ] **Step 3: SlugGenerator 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/SlugGenerator.java`:
```java
package com.genderreveal.api.page;

import java.security.SecureRandom;

public class SlugGenerator {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.SlugGeneratorTest"`
예상 결과: PASS

- [ ] **Step 5: 실패하는 UniqueSlugAllocator 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/page/UniqueSlugAllocatorTest.java`:
```java
package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UniqueSlugAllocatorTest {

    @Mock
    private PageRepository pageRepository;

    @Test
    void returnsFirstCandidateWhenNotTaken() {
        SlugGenerator fixedGenerator = new SlugGenerator() {
            @Override
            public String generate() {
                return "freeslug1";
            }
        };
        when(pageRepository.existsBySlug("freeslug1")).thenReturn(false);

        UniqueSlugAllocator allocator = new UniqueSlugAllocator(fixedGenerator, pageRepository);

        assertThat(allocator.allocate()).isEqualTo("freeslug1");
    }

    @Test
    void throwsAfterExhaustingAttemptsWhenAlwaysTaken() {
        when(pageRepository.existsBySlug(anyString())).thenReturn(true);

        UniqueSlugAllocator allocator = new UniqueSlugAllocator(new SlugGenerator(), pageRepository);

        assertThatThrownBy(allocator::allocate).isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 6: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.UniqueSlugAllocatorTest"`
예상 결과: 컴파일 실패 — `UniqueSlugAllocator`가 아직 없음

- [ ] **Step 7: UniqueSlugAllocator 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/UniqueSlugAllocator.java`:
```java
package com.genderreveal.api.page;

import org.springframework.stereotype.Component;

@Component
public class UniqueSlugAllocator {

    private static final int MAX_ATTEMPTS = 10;

    private final SlugGenerator slugGenerator;
    private final PageRepository pageRepository;

    public UniqueSlugAllocator(SlugGenerator slugGenerator, PageRepository pageRepository) {
        this.slugGenerator = slugGenerator;
        this.pageRepository = pageRepository;
    }

    public String allocate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = slugGenerator.generate();
            if (!pageRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to allocate a unique slug after " + MAX_ATTEMPTS + " attempts");
    }
}
```

`SlugGenerator`도 스프링 빈으로 주입되도록 `@Component`를 붙인다 — `gender-reveal/api/src/main/java/com/genderreveal/api/page/SlugGenerator.java`의 클래스 선언을 `public class SlugGenerator`에서 다음으로 수정:
```java
@org.springframework.stereotype.Component
public class SlugGenerator {
```

- [ ] **Step 8: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.UniqueSlugAllocatorTest"`
예상 결과: PASS

- [ ] **Step 9: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/page/SlugGenerator.java api/src/main/java/com/genderreveal/api/page/UniqueSlugAllocator.java api/src/test/java/com/genderreveal/api/page/SlugGeneratorTest.java api/src/test/java/com/genderreveal/api/page/UniqueSlugAllocatorTest.java
git commit -m "feat(api): 슬러그 생성 및 유일성 할당 로직 추가"
```

---

### Task 5: 페이지 상태 계산 로직

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageStatus.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageStatusCalculator.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/page/PageStatusCalculatorTest.java`

**Interfaces:**
- Consumes: `Page.getRevealAt(): Instant`, `Page.getExpiresAt(): Instant` (Task 3)
- Produces: `PageStatus`(SECRET/OPEN/EXPIRED), `PageStatusCalculator.calculate(Page, Instant): PageStatus`

- [ ] **Step 1: 실패하는 상태 계산 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/page/PageStatusCalculatorTest.java`:
```java
package com.genderreveal.api.page;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PageStatusCalculatorTest {

    private final PageStatusCalculator calculator = new PageStatusCalculator();

    @Test
    void secretBeforeRevealAt() {
        Instant now = Instant.now();
        Page page = pageWith(now.plus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS));

        assertThat(calculator.calculate(page, now)).isEqualTo(PageStatus.SECRET);
    }

    @Test
    void openBetweenRevealAtAndExpiresAt() {
        Instant now = Instant.now();
        Page page = pageWith(now.minus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS));

        assertThat(calculator.calculate(page, now)).isEqualTo(PageStatus.OPEN);
    }

    @Test
    void expiredAfterExpiresAt() {
        Instant now = Instant.now();
        Page page = pageWith(now.minus(31, ChronoUnit.DAYS), now.minus(1, ChronoUnit.SECONDS));

        assertThat(calculator.calculate(page, now)).isEqualTo(PageStatus.EXPIRED);
    }

    @Test
    void expiredExactlyAtBoundary() {
        Instant now = Instant.now();
        Page page = pageWith(now.minus(31, ChronoUnit.DAYS), now);

        assertThat(calculator.calculate(page, now)).isEqualTo(PageStatus.EXPIRED);
    }

    private Page pageWith(Instant revealAt, Instant expiresAt) {
        return new Page(
            "slug", "닉네임", "boy", revealAt, null, "메시지", "box", false,
            "owner@example.com", Instant.now(), expiresAt
        );
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.PageStatusCalculatorTest"`
예상 결과: 컴파일 실패 — `PageStatus`, `PageStatusCalculator`가 아직 없음

- [ ] **Step 3: PageStatus, PageStatusCalculator 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageStatus.java`:
```java
package com.genderreveal.api.page;

public enum PageStatus {
    SECRET,
    OPEN,
    EXPIRED
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageStatusCalculator.java`:
```java
package com.genderreveal.api.page;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PageStatusCalculator {

    public PageStatus calculate(Page page, Instant now) {
        if (now.isBefore(page.getRevealAt())) {
            return PageStatus.SECRET;
        }
        if (!now.isBefore(page.getExpiresAt())) {
            return PageStatus.EXPIRED;
        }
        return PageStatus.OPEN;
    }
}
```

- [ ] **Step 4: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.PageStatusCalculatorTest"`
예상 결과: PASS (4개 테스트 모두)

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/page/PageStatus.java api/src/main/java/com/genderreveal/api/page/PageStatusCalculator.java api/src/test/java/com/genderreveal/api/page/PageStatusCalculatorTest.java
git commit -m "feat(api): 페이지 상태(secret/open/expired) 계산 로직 추가"
```

---

### Task 6: 페이지 생성 API (`POST /api/pages`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/config/ClockConfig.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageCreateRequest.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageCreateResponse.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/SlugAlreadyTakenException.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageService.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageController.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageExceptionHandler.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/page/PageControllerCreateTest.java`

**Interfaces:**
- Consumes: `PageRepository`(Task 3), `UniqueSlugAllocator`(Task 4)
- Produces: `POST /api/pages` → 201 + `PageCreateResponse`, `PageService.create(PageCreateRequest): Page`

- [ ] **Step 1: Clock 빈 설정 추가**

`gender-reveal/api/src/main/java/com/genderreveal/api/config/ClockConfig.java`:
```java
package com.genderreveal.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 2: 실패하는 컨트롤러 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/page/PageControllerCreateTest.java`:
```java
package com.genderreveal.api.page;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PageControllerCreateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsPageAndReturnsGeneratedSlug() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "ownerEmail", "owner@example.com"
        );

        mockMvc.perform(post("/api/pages")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").isNotEmpty())
            .andExpect(jsonPath("$.nickname").value("뽀튼이"));
    }

    @Test
    void rejectsInvalidGender() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "unknown",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "ownerEmail", "owner@example.com"
        );

        mockMvc.perform(post("/api/pages")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateCustomSlug() throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            "theme", "box",
            "bgmEnabled", true,
            "ownerEmail", "owner@example.com",
            "slug", "duplicate-slug"
        );

        mockMvc.perform(post("/api/pages")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pages")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 3: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.PageControllerCreateTest"`
예상 결과: 컴파일 실패 — DTO/서비스/컨트롤러가 아직 없음

- [ ] **Step 4: DTO 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageCreateRequest.java`:
```java
package com.genderreveal.api.page;

import jakarta.validation.constraints.Email;
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
    @NotBlank @Email String ownerEmail,
    @Pattern(regexp = "[a-z0-9-]{3,32}") String slug
) {}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageCreateResponse.java`:
```java
package com.genderreveal.api.page;

import java.time.Instant;
import java.time.LocalDate;

public record PageCreateResponse(
    String slug,
    String nickname,
    Instant revealAt,
    LocalDate dueDate,
    String message,
    String theme,
    boolean bgmEnabled,
    Instant createdAt,
    Instant expiresAt
) {
    static PageCreateResponse from(Page page) {
        return new PageCreateResponse(
            page.getSlug(), page.getNickname(), page.getRevealAt(), page.getDueDate(),
            page.getMessage(), page.getTheme(), page.isBgmEnabled(),
            page.getCreatedAt(), page.getExpiresAt()
        );
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/page/SlugAlreadyTakenException.java`:
```java
package com.genderreveal.api.page;

public class SlugAlreadyTakenException extends RuntimeException {
    public SlugAlreadyTakenException(String slug) {
        super("Slug already taken: " + slug);
    }
}
```

- [ ] **Step 5: PageService 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageService.java`:
```java
package com.genderreveal.api.page;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class PageService {

    private static final int RETENTION_DAYS = 30;

    private final PageRepository pageRepository;
    private final UniqueSlugAllocator slugAllocator;
    private final Clock clock;

    public PageService(PageRepository pageRepository, UniqueSlugAllocator slugAllocator, Clock clock) {
        this.pageRepository = pageRepository;
        this.slugAllocator = slugAllocator;
        this.clock = clock;
    }

    public Page create(PageCreateRequest request) {
        String slug = resolveSlug(request.slug());
        Instant now = Instant.now(clock);

        Page page = new Page(
            slug, request.nickname(), request.actualGender(), request.revealAt(),
            request.dueDate(), request.message(), request.theme(), request.bgmEnabled(),
            request.ownerEmail(), now, now.plus(RETENTION_DAYS, ChronoUnit.DAYS)
        );

        return pageRepository.save(page);
    }

    private String resolveSlug(String requestedSlug) {
        if (requestedSlug == null || requestedSlug.isBlank()) {
            return slugAllocator.allocate();
        }
        if (pageRepository.existsBySlug(requestedSlug)) {
            throw new SlugAlreadyTakenException(requestedSlug);
        }
        return requestedSlug;
    }
}
```

- [ ] **Step 6: PageController, 예외 핸들러 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageController.java`:
```java
package com.genderreveal.api.page;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/pages")
public class PageController {

    private final PageService pageService;

    public PageController(PageService pageService) {
        this.pageService = pageService;
    }

    @PostMapping
    public ResponseEntity<PageCreateResponse> create(@Valid @RequestBody PageCreateRequest request) {
        Page page = pageService.create(request);
        PageCreateResponse response = PageCreateResponse.from(page);
        return ResponseEntity.created(URI.create("/api/pages/" + page.getSlug())).body(response);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageExceptionHandler.java`:
```java
package com.genderreveal.api.page;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PageExceptionHandler {

    @ExceptionHandler(SlugAlreadyTakenException.class)
    public ResponseEntity<Map<String, String>> handleSlugTaken(SlugAlreadyTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
```

- [ ] **Step 7: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.PageControllerCreateTest"`
예상 결과: PASS (3개 테스트 모두)

- [ ] **Step 8: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/config/ClockConfig.java api/src/main/java/com/genderreveal/api/page/
git add api/src/test/java/com/genderreveal/api/page/PageControllerCreateTest.java
git commit -m "feat(api): 페이지 생성 API(POST /api/pages) 추가"
```

---

### Task 7: 페이지 조회 API (`GET /api/pages/{slug}`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PagePublicResponse.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageNotFoundException.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageController.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageExceptionHandler.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/page/PageControllerGetTest.java`

**Interfaces:**
- Consumes: `PageStatusCalculator.calculate(Page, Instant): PageStatus`(Task 5), `PageRepository.findBySlug`(Task 3)
- Produces: `GET /api/pages/{slug}` → 200 + `PagePublicResponse` | 404, `PageService.getPublicView(String): PagePublicResponse`

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/page/PageControllerGetTest.java`:
```java
package com.genderreveal.api.page;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PageControllerGetTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsSecretStatusBeforeRevealAt() throws Exception {
        String slug = createPage("future-slug", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/pages/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("secret"))
            .andExpect(jsonPath("$.actualGender").doesNotExist());
    }

    @Test
    void returnsOpenStatusAndActualGenderAfterRevealAt() throws Exception {
        String slug = createPage("past-slug", Instant.now().minus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/pages/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("open"))
            .andExpect(jsonPath("$.actualGender").value("boy"));
    }

    @Test
    void returns404ForUnknownSlug() throws Exception {
        mockMvc.perform(get("/api/pages/does-not-exist"))
            .andExpect(status().isNotFound());
    }

    private String createPage(String slug, Instant revealAt) throws Exception {
        Map<String, Object> body = Map.of(
            "nickname", "뽀튼이",
            "actualGender", "boy",
            "revealAt", revealAt.toString(),
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
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.PageControllerGetTest"`
예상 결과: 컴파일 실패 또는 404 — `GET /api/pages/{slug}`가 아직 없음

- [ ] **Step 3: PagePublicResponse, PageNotFoundException 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PagePublicResponse.java`:
```java
package com.genderreveal.api.page;

import java.time.LocalDate;

public record PagePublicResponse(
    String status,
    String nickname,
    String actualGender,
    LocalDate dueDate,
    String message,
    String theme,
    Boolean bgmEnabled
) {
    static PagePublicResponse secretOrExpired(String status, String nickname) {
        return new PagePublicResponse(status, nickname, null, null, null, null, null);
    }

    static PagePublicResponse open(Page page) {
        return new PagePublicResponse(
            "open", page.getNickname(), page.getActualGender(), page.getDueDate(),
            page.getMessage(), page.getTheme(), page.isBgmEnabled()
        );
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageNotFoundException.java`:
```java
package com.genderreveal.api.page;

public class PageNotFoundException extends RuntimeException {
    public PageNotFoundException(String slug) {
        super("Page not found: " + slug);
    }
}
```

- [ ] **Step 4: PageService에 상태 계산 조회 메서드 추가**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageService.java`를 수정한다 — 생성자에 `PageStatusCalculator` 주입을 추가하고 `getPublicView` 메서드를 추가:

```java
package com.genderreveal.api.page;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class PageService {

    private static final int RETENTION_DAYS = 30;

    private final PageRepository pageRepository;
    private final UniqueSlugAllocator slugAllocator;
    private final PageStatusCalculator statusCalculator;
    private final Clock clock;

    public PageService(PageRepository pageRepository, UniqueSlugAllocator slugAllocator,
                        PageStatusCalculator statusCalculator, Clock clock) {
        this.pageRepository = pageRepository;
        this.slugAllocator = slugAllocator;
        this.statusCalculator = statusCalculator;
        this.clock = clock;
    }

    public Page create(PageCreateRequest request) {
        String slug = resolveSlug(request.slug());
        Instant now = Instant.now(clock);

        Page page = new Page(
            slug, request.nickname(), request.actualGender(), request.revealAt(),
            request.dueDate(), request.message(), request.theme(), request.bgmEnabled(),
            request.ownerEmail(), now, now.plus(RETENTION_DAYS, ChronoUnit.DAYS)
        );

        return pageRepository.save(page);
    }

    public PagePublicResponse getPublicView(String slug) {
        Page page = pageRepository.findBySlug(slug)
            .orElseThrow(() -> new PageNotFoundException(slug));

        PageStatus status = statusCalculator.calculate(page, Instant.now(clock));

        return switch (status) {
            case SECRET -> PagePublicResponse.secretOrExpired("secret", page.getNickname());
            case EXPIRED -> PagePublicResponse.secretOrExpired("expired", page.getNickname());
            case OPEN -> PagePublicResponse.open(page);
        };
    }

    private String resolveSlug(String requestedSlug) {
        if (requestedSlug == null || requestedSlug.isBlank()) {
            return slugAllocator.allocate();
        }
        if (pageRepository.existsBySlug(requestedSlug)) {
            throw new SlugAlreadyTakenException(requestedSlug);
        }
        return requestedSlug;
    }
}
```

- [ ] **Step 5: PageController에 조회 엔드포인트 추가**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageController.java`에 다음 메서드와 import를 추가:

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
```

```java
    @GetMapping("/{slug}")
    public PagePublicResponse getBySlug(@PathVariable String slug) {
        return pageService.getPublicView(slug);
    }
```

- [ ] **Step 6: PageExceptionHandler에 404 처리 추가**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageExceptionHandler.java`:
```java
package com.genderreveal.api.page;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PageExceptionHandler {

    @ExceptionHandler(SlugAlreadyTakenException.class)
    public ResponseEntity<Map<String, String>> handleSlugTaken(SlugAlreadyTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PageNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(PageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
```

- [ ] **Step 7: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.PageControllerGetTest"`
예상 결과: PASS (3개 테스트 모두)

- [ ] **Step 8: 전체 테스트 스위트 실행**

실행: `cd gender-reveal/api && ./gradlew test`
예상 결과: 모든 테스트 PASS (Task 1~7에서 작성한 테스트 전체)

- [ ] **Step 9: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/page/ api/src/test/java/com/genderreveal/api/page/PageControllerGetTest.java
git commit -m "feat(api): 페이지 조회 API(GET /api/pages/{slug}) 및 상태 노출 추가"
```

---

### Task 8: Docker화 + docker-compose

**Files:**
- Create: `gender-reveal/api/Dockerfile`
- Create: `gender-reveal/docker-compose.yml`
- Modify: `gender-reveal/.gitignore`

**Interfaces:**
- Consumes: Task 1~7에서 완성된 `gender-reveal-api` 애플리케이션
- Produces: `docker compose up -d api`로 뜨는 컨테이너, 호스트 `localhost:8080`에 노출

- [ ] **Step 1: Dockerfile 작성**

`gender-reveal/api/Dockerfile`:
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p /app/data
ENV GENDER_REVEAL_DB_PATH=/app/data/gender-reveal.db
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: docker-compose.yml 작성**

`gender-reveal/docker-compose.yml`:
```yaml
services:
  api:
    build:
      context: ./api
    ports:
      - "8080:8080"
    volumes:
      - api-data:/app/data

volumes:
  api-data:
```

- [ ] **Step 3: .gitignore에 Docker 관련 항목 확인**

`gender-reveal/.gitignore`에 아래가 없다면 추가한다 (Task 1에서 `api/data/`는 이미 추가했으므로 중복 확인만):
```
gender-reveal/api/build/
gender-reveal/api/.gradle/
gender-reveal/api/data/
```

- [ ] **Step 4: 컨테이너 빌드 및 기동**

실행:
```bash
cd gender-reveal
docker compose build api
docker compose up -d api
```
예상 결과: 빌드/기동 성공, `docker compose ps`에 `api`가 `running` 상태

- [ ] **Step 5: 헬스체크 수동 검증**

실행: `curl -s http://localhost:8080/api/health`
예상 결과: `{"status":"ok"}`

- [ ] **Step 6: 페이지 생성/조회 수동 검증**

실행:
```bash
curl -s -X POST http://localhost:8080/api/pages \
  -H "Content-Type: application/json" \
  -d '{"nickname":"뽀튼이","actualGender":"boy","revealAt":"2026-09-19T00:00:00Z","theme":"box","bgmEnabled":true,"ownerEmail":"owner@example.com"}'
```
예상 결과: 201, `slug` 필드 포함 응답

응답에서 받은 `slug` 값으로:
```bash
curl -s http://localhost:8080/api/pages/<slug>
```
예상 결과: `{"status":"open",...}` (revealAt을 과거로 지정했으므로 즉시 open)

- [ ] **Step 7: 컨테이너 정리**

실행: `docker compose down`
예상 결과: 컨테이너 정상 종료 (volume `api-data`는 유지됨)

- [ ] **Step 8: 커밋**

```bash
cd gender-reveal
git add api/Dockerfile docker-compose.yml .gitignore
git commit -m "feat(infra): API Docker화 및 docker-compose 설정 추가"
```

---

## 이 계획 완료 후 상태

- `docker compose up -d api`로 SQLite 기반 Spring Boot API가 뜨고, `POST /api/pages`로 페이지를 만들고 `GET /api/pages/{slug}`로 상태 기반 조회가 가능하다.
- 인증, 맞추기/방명록 API, nginx 리버스 프록시, 프론트엔드는 아직 없다 — 각각 Plan 2, 3, 4에서 이어간다.

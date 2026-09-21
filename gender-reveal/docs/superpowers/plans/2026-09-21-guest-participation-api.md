# 젠더리빌 방문자 참여 API 구현 계획 (Plan 2: 맞추기 + 방명록)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Plan 1의 페이지 API 위에 게스트 참여 기능(맞추기, 방명록)을 추가하고, Plan 1 최종 리뷰에서 지적된 예외 처리 설계 결함(전역 핸들러가 범용 예외 타입을 잡아 다음 기능과 충돌할 위험)을 먼저 해소한다.

**Architecture:** `com.genderreveal.api.guess`, `com.genderreveal.api.guestbook` 두 개의 신규 패키지를 `com.genderreveal.api.page`와 동일한 계층(엔티티 → Repository → Service → Controller)으로 추가한다. 두 기능 모두 대상 페이지가 `open` 상태일 때만 동작하도록 `PageStatusCalculator`(Plan 1)를 재사용해 게이트를 건다. 맞추기는 로그인 없이 쿠키(`guest_id`)로 게스트를 식별해 1인 1회를 보장한다.

**Tech Stack:** Plan 1과 동일 (Java 21, Spring Boot 3, Spring Data JPA, SQLite). 신규 의존성 없음.

**Spec:** [docs/superpowers/specs/2026-09-18-implementation-design.md](../specs/2026-09-18-implementation-design.md) (4절 데이터 모델의 `guesses`/`guestbook_entries` 테이블, 5절 API 설계의 공개 엔드포인트)

## 이 계획에서 스펙을 넘어 내린 설계 판단

스펙 4~5절은 테이블 스키마와 엔드포인트 목록만 정의하고 다음은 명시하지 않았다. 구현 과정에서 다음과 같이 정했다.

- **게스트 쿠키 발급 시점**: `POST /api/pages/{slug}/guesses` 최초 호출 시, 요청에 `guest_id` 쿠키가 없으면 서버가 UUID를 생성해 응답에 `Set-Cookie`로 내려준다. 별도의 "쿠키 발급 전용" 엔드포인트는 두지 않는다.
- **맞추기/방명록의 페이지 상태 게이트**: 두 기능 모두 대상 페이지가 `open`이 아니면(즉 `secret` 또는 `expired`) 409로 거부한다. 스펙 4절에 "만료 시 열람만 제한"이라고 되어 있어, `expired` 페이지에 새 맞추기/방명록이 쌓이는 것은 이 문구와 맞지 않는다고 판단했다. `secret` 상태에서 거부하는 것은 애초에 게스트가 그 화면에 도달할 방법이 없어 방어적 조치에 가깝다.
- **Plan 1 최종 리뷰의 이월된 지적 해소**: `PageExceptionHandler`가 `DataIntegrityViolationException`/`IllegalStateException`을 범용으로 잡고 있어, 이번에 추가하는 맞추기 API가 동시 요청으로 유니크 제약을 위반하면 엉뚱하게 "Slug already taken" 메시지를 받는 문제가 실제로 발생한다. Task 3에서 먼저 이를 각 서비스 계층에서 구체적인 예외로 변환하도록 리팩터링한다.

## Global Constraints

- 언어/런타임: Java 21, Gradle (Plan 1과 동일)
- DB 접근: Spring Data JPA + Hibernate 커뮤니티 SQLite dialect, SQLite FK 강제(`foreign_keys=on`)가 이미 켜져 있음 — **모든 신규 엔티티는 실제로 존재하는 `pages.id`를 참조해야 하며, 테스트에서도 더미 ID를 쓸 수 없다** (아래 Task 1/2의 테스트 참고)
- `Instant` 컬럼은 `InstantStringConverter`(`com.genderreveal.api.common`, Plan 1에서 이미 존재)를 반드시 적용해 ISO-8601 문자열로 저장한다 — 새 엔티티도 예외 없음
- 페이지 상태 판정은 항상 `PageStatusCalculator.calculate(Page, Instant.now(clock))`를 재사용한다 — 직접 `revealAt`/`expiresAt` 비교 로직을 새로 작성하지 않는다
- API 스타일: REST, `/api/pages/{slug}/...` 하위 경로
- 패키지 루트: `com.genderreveal.api`

---

### Task 1: Guess 엔티티 + Repository

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/Guess.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessRepository.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/guess/GuessRepositoryTest.java`

**Interfaces:**
- Consumes: `com.genderreveal.api.common.InstantStringConverter`(Plan 1), `com.genderreveal.api.page.Page`/`PageRepository`(Plan 1, 테스트에서 FK를 만족하는 페이지를 만들기 위해 사용)
- Produces: `Guess`(getId/getPageId/getGuestCookieId/getGuessedGender/getCreatedAt), `GuessRepository.existsByPageIdAndGuestCookieId(Long, String): boolean`

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/guess/GuessRepositoryTest.java`:
```java
package com.genderreveal.api.guess;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class GuessRepositoryTest {

    @Autowired
    private GuessRepository guessRepository;

    @Autowired
    private PageRepository pageRepository;

    @Test
    void savesAndDetectsExistingGuestGuess() {
        Page page = pageRepository.save(samplePage());

        Guess guess = new Guess(page.getId(), "guest-abc", "boy", Instant.now());
        guessRepository.save(guess);

        assertThat(guessRepository.existsByPageIdAndGuestCookieId(page.getId(), "guest-abc")).isTrue();
    }

    @Test
    void existsByPageIdAndGuestCookieIdReflectsUnsavedCombination() {
        Page page = pageRepository.save(samplePage());

        assertThat(guessRepository.existsByPageIdAndGuestCookieId(page.getId(), "nobody")).isFalse();
    }

    private Page samplePage() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new Page(
            "guess-test-" + System.nanoTime(), "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "owner@example.com", now, now.plus(30, ChronoUnit.DAYS)
        );
    }
}
```

**중요:** `guesses.page_id`는 실제 FK 제약(SQLite `foreign_keys=on`, Plan 1에서 활성화됨)을 받는다. 존재하지 않는 `pageId`(예: 리터럴 `1L`)로 `Guess`를 저장하면 제약 위반으로 실패한다. 위 테스트처럼 항상 `pageRepository.save(...)`로 실제 `Page`를 먼저 만들고 그 `getId()`를 써야 한다.

- [ ] **Step 2: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guess.GuessRepositoryTest"`
예상 결과: 컴파일 실패 — `Guess`, `GuessRepository`가 아직 없음

- [ ] **Step 3: Guess 엔티티 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/guess/Guess.java`:
```java
package com.genderreveal.api.guess;

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
@Table(name = "guesses")
public class Guess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(name = "guest_cookie_id", nullable = false)
    private String guestCookieId;

    @Column(name = "guessed_gender", nullable = false)
    private String guessedGender;

    @Column(name = "created_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant createdAt;

    protected Guess() {
        // JPA
    }

    public Guess(Long pageId, String guestCookieId, String guessedGender, Instant createdAt) {
        this.pageId = pageId;
        this.guestCookieId = guestCookieId;
        this.guessedGender = guessedGender;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getPageId() { return pageId; }
    public String getGuestCookieId() { return guestCookieId; }
    public String getGuessedGender() { return guessedGender; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: GuessRepository 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessRepository.java`:
```java
package com.genderreveal.api.guess;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuessRepository extends JpaRepository<Guess, Long> {
    boolean existsByPageIdAndGuestCookieId(Long pageId, String guestCookieId);
}
```

- [ ] **Step 5: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guess.GuessRepositoryTest"`
예상 결과: PASS

- [ ] **Step 6: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/guess/Guess.java api/src/main/java/com/genderreveal/api/guess/GuessRepository.java api/src/test/java/com/genderreveal/api/guess/GuessRepositoryTest.java
git commit -m "feat(api): Guess 엔티티와 Repository 추가"
```

---

### Task 2: GuestbookEntry 엔티티 + Repository

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntry.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryRepository.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryRepositoryTest.java`

**Interfaces:**
- Consumes: `com.genderreveal.api.common.InstantStringConverter`, `com.genderreveal.api.page.Page`/`PageRepository`
- Produces: `GuestbookEntry`(getId/getPageId/getNickname/getMessage/isHidden/getCreatedAt, `hide()` 뮤테이터), `GuestbookEntryRepository.findByPageIdAndHiddenFalseOrderByCreatedAtDesc(Long): List<GuestbookEntry>`

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryRepositoryTest.java`:
```java
package com.genderreveal.api.guestbook;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class GuestbookEntryRepositoryTest {

    @Autowired
    private GuestbookEntryRepository guestbookEntryRepository;

    @Autowired
    private PageRepository pageRepository;

    @Test
    void listsVisibleEntriesNewestFirstAndExcludesHidden() {
        Page page = pageRepository.save(samplePage());

        Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS);
        GuestbookEntry older = guestbookEntryRepository.save(
            new GuestbookEntry(page.getId(), "이모", "축하해요", t1));
        GuestbookEntry newer = guestbookEntryRepository.save(
            new GuestbookEntry(page.getId(), "삼촌", "고생하셨어요", t2));
        GuestbookEntry hidden = guestbookEntryRepository.save(
            new GuestbookEntry(page.getId(), "스팸", "광고", Instant.now()));
        hidden.hide();
        guestbookEntryRepository.save(hidden);

        List<GuestbookEntry> visible = guestbookEntryRepository
            .findByPageIdAndHiddenFalseOrderByCreatedAtDesc(page.getId());

        assertThat(visible).extracting(GuestbookEntry::getNickname)
            .containsExactly("삼촌", "이모");
    }

    private Page samplePage() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new Page(
            "guestbook-test-" + System.nanoTime(), "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "owner@example.com", now, now.plus(30, ChronoUnit.DAYS)
        );
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guestbook.GuestbookEntryRepositoryTest"`
예상 결과: 컴파일 실패 — `GuestbookEntry`, `GuestbookEntryRepository`가 아직 없음

- [ ] **Step 3: GuestbookEntry 엔티티 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntry.java`:
```java
package com.genderreveal.api.guestbook;

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
@Table(name = "guestbook_entries")
public class GuestbookEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false)
    @Convert(converter = InstantStringConverter.class)
    private Instant createdAt;

    protected GuestbookEntry() {
        // JPA
    }

    public GuestbookEntry(Long pageId, String nickname, String message, Instant createdAt) {
        this.pageId = pageId;
        this.nickname = nickname;
        this.message = message;
        this.hidden = false;
        this.createdAt = createdAt;
    }

    public void hide() {
        this.hidden = true;
    }

    public Long getId() { return id; }
    public Long getPageId() { return pageId; }
    public String getNickname() { return nickname; }
    public String getMessage() { return message; }
    public boolean isHidden() { return hidden; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`hide()`는 이번 태스크의 관리 기능은 아니지만(방명록 숨김/노출 API는 Plan 3/5의 관리자 대시보드), 스펙에 이미 명시된 예정 기능이고 이 테스트가 "숨김 항목 제외" 동작을 검증하려면 숨긴 상태를 만들 방법이 필요해서 최소한의 뮤테이터로 미리 추가한다.

- [ ] **Step 4: GuestbookEntryRepository 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryRepository.java`:
```java
package com.genderreveal.api.guestbook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuestbookEntryRepository extends JpaRepository<GuestbookEntry, Long> {
    List<GuestbookEntry> findByPageIdAndHiddenFalseOrderByCreatedAtDesc(Long pageId);
}
```

- [ ] **Step 5: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guestbook.GuestbookEntryRepositoryTest"`
예상 결과: PASS

- [ ] **Step 6: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntry.java api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryRepository.java api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryRepositoryTest.java
git commit -m "feat(api): GuestbookEntry 엔티티와 Repository 추가"
```

---

### Task 3: 예외 처리 리팩터링 (Plan 1 최종 리뷰 이월 사항 해소)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/SlugAllocationExhaustedException.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageNotOpenException.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/UniqueSlugAllocator.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/page/PageExceptionHandler.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/page/UniqueSlugAllocatorTest.java`

**Interfaces:**
- Produces: `SlugAllocationExhaustedException`, `PageNotOpenException`(Task 4/5/6에서 맞추기·방명록 서비스가 던짐, `PageExceptionHandler`가 전역으로 409 처리하므로 별도 핸들러 불필요)

**배경:** Plan 1 최종 리뷰가 남긴 Minor 지적 — `PageExceptionHandler`의 `DataIntegrityViolationException`/`IllegalStateException` 핸들러가 "어떤 기능에서 왔든" 잡아버려서, 이번에 추가하는 맞추기 API가 동시 요청으로 유니크 제약을 위반하면 엉뚱하게 "Slug already taken"을 받는다. 각 서비스가 자기 영역의 레이스 컨디션을 로컬에서 구체적인 예외로 변환하도록 정리한다.

- [ ] **Step 1: UniqueSlugAllocator가 전용 예외를 던지도록 수정**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/SlugAllocationExhaustedException.java`:
```java
package com.genderreveal.api.page;

public class SlugAllocationExhaustedException extends RuntimeException {
    public SlugAllocationExhaustedException(String message) {
        super(message);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/page/UniqueSlugAllocator.java`의 `throw new IllegalStateException(...)` 줄을:
```java
        throw new SlugAllocationExhaustedException("Failed to allocate a unique slug after " + MAX_ATTEMPTS + " attempts");
```
로 교체한다 (import 불필요, 같은 패키지).

- [ ] **Step 2: 기존 UniqueSlugAllocatorTest의 예외 타입 단언 수정**

`gender-reveal/api/src/test/java/com/genderreveal/api/page/UniqueSlugAllocatorTest.java`에서 `throwsAfterExhaustingAttemptsWhenAlwaysTaken` 테스트의
```java
assertThatThrownBy(allocator::allocate).isInstanceOf(IllegalStateException.class);
```
를
```java
assertThatThrownBy(allocator::allocate).isInstanceOf(SlugAllocationExhaustedException.class);
```
로 바꾼다.

- [ ] **Step 3: 테스트 실행해 통과 확인 (이 시점까지)**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.page.UniqueSlugAllocatorTest"`
예상 결과: PASS

- [ ] **Step 4: PageNotOpenException 추가**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageNotOpenException.java`:
```java
package com.genderreveal.api.page;

public class PageNotOpenException extends RuntimeException {
    public PageNotOpenException(String slug) {
        super("Page is not open: " + slug);
    }
}
```

- [ ] **Step 5: PageService가 슬러그 레이스를 로컬에서 변환하도록 수정**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageService.java`의 `create` 메서드 본문을 다음으로 교체 (다른 메서드는 그대로 유지):
```java
    public Page create(PageCreateRequest request) {
        Instant now = Instant.now(clock);
        validateRevealAt(request.revealAt(), now);

        String slug = resolveSlug(request.slug());

        Page page = new Page(
            slug, request.nickname(), request.actualGender(), request.revealAt(),
            request.dueDate(), request.message(), request.theme(), request.bgmEnabled(),
            request.ownerEmail(), now, now.plus(RETENTION_DAYS, ChronoUnit.DAYS)
        );

        try {
            return pageRepository.save(page);
        } catch (DataIntegrityViolationException ex) {
            throw new SlugAlreadyTakenException(slug);
        }
    }
```
파일 상단의 import 목록에 다음을 추가:
```java
import org.springframework.dao.DataIntegrityViolationException;
```

- [ ] **Step 6: PageExceptionHandler에서 전역 핸들러를 걷어내고 전용 예외 핸들러로 교체**

`gender-reveal/api/src/main/java/com/genderreveal/api/page/PageExceptionHandler.java` 전체를 다음으로 교체:
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

    @ExceptionHandler(InvalidRevealAtException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRevealAt(InvalidRevealAtException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SlugAllocationExhaustedException.class)
    public ResponseEntity<Map<String, String>> handleSlugAllocationExhausted(SlugAllocationExhaustedException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PageNotOpenException.class)
    public ResponseEntity<Map<String, String>> handlePageNotOpen(PageNotOpenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
```

이제 `DataIntegrityViolationException`/`IllegalStateException`을 범용으로 잡는 핸들러는 없다 — 각 기능(페이지, 이후 맞추기)이 자기 서비스 계층에서 레이스를 자기 예외 타입으로 변환하고, 이 어드바이스는 이름이 명확한 도메인 예외 타입만 처리한다.

- [ ] **Step 7: 전체 스위트 실행해 회귀 없는지 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상 결과: 전체 PASS (Plan 1의 모든 기존 테스트 포함, 특히 `PageControllerCreateTest`의 `rejectsDuplicateCustomSlug`가 여전히 409를 받는지 — 이제 `resolveSlug`의 사전 체크가 아니라 `create`의 `catch` 블록을 거치지는 않지만, 사전 체크가 먼저 걸려서 동일하게 409가 나와야 한다)

- [ ] **Step 8: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/page/SlugAllocationExhaustedException.java api/src/main/java/com/genderreveal/api/page/PageNotOpenException.java api/src/main/java/com/genderreveal/api/page/UniqueSlugAllocator.java api/src/main/java/com/genderreveal/api/page/PageService.java api/src/main/java/com/genderreveal/api/page/PageExceptionHandler.java api/src/test/java/com/genderreveal/api/page/UniqueSlugAllocatorTest.java
git commit -m "refactor(api): 예외 처리를 서비스별 전용 타입으로 정리 (Plan 1 이월 사항)"
```

---

### Task 4: 맞추기 API (`POST /api/pages/{slug}/guesses`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessCreateRequest.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessResponse.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/DuplicateGuessException.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessExceptionHandler.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessService.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessController.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/guess/GuessControllerTest.java`

**Interfaces:**
- Consumes: `GuessRepository`(Task 1), `com.genderreveal.api.page.PageRepository`/`PageStatusCalculator`/`PageStatus`/`PageNotFoundException`/`PageNotOpenException`(Plan 1 + Task 3)
- Produces: `POST /api/pages/{slug}/guesses` → 201 + `Set-Cookie: guest_id=...` + `GuessResponse` | 404 | 409

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`gender-reveal/api/src/test/java/com/genderreveal/api/guess/GuessControllerTest.java`:
```java
package com.genderreveal.api.guess;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GuessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void firstGuessIssuesCookieAndSucceeds() throws Exception {
        String slug = createOpenPage("guess-first-slug");

        MvcResult result = mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isCreated())
            .andExpect(cookie().exists("guest_id"))
            .andExpect(jsonPath("$.guessedGender").value("boy"))
            .andReturn();

        String guestId = result.getResponse().getCookie("guest_id").getValue();
        assertThat(guestId).isNotBlank();
    }

    @Test
    void secondGuessWithSameCookieIsRejected() throws Exception {
        String slug = createOpenPage("guess-dup-slug");
        MockCookie cookie = new MockCookie("guest_id", "repeat-guest");

        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .cookie(cookie)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .cookie(cookie)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "girl"))))
            .andExpect(status().isConflict());
    }

    @Test
    void guessOnSecretPageIsRejected() throws Exception {
        String slug = createPage("guess-secret-slug", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/pages/" + slug + "/guesses")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isConflict());
    }

    @Test
    void guessOnUnknownSlugReturns404() throws Exception {
        mockMvc.perform(post("/api/pages/does-not-exist/guesses")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("guessedGender", "boy"))))
            .andExpect(status().isNotFound());
    }

    private String createOpenPage(String slug) throws Exception {
        return createPage(slug, Instant.now().minus(1, ChronoUnit.HOURS));
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

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guess.GuessControllerTest"`
예상 결과: 컴파일 실패 — DTO/서비스/컨트롤러가 아직 없음

- [ ] **Step 3: DTO와 예외 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessCreateRequest.java`:
```java
package com.genderreveal.api.guess;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record GuessCreateRequest(
    @NotNull @Pattern(regexp = "boy|girl") String guessedGender
) {}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessResponse.java`:
```java
package com.genderreveal.api.guess;

import java.time.Instant;

public record GuessResponse(String guessedGender, Instant createdAt) {
    static GuessResponse from(Guess guess) {
        return new GuessResponse(guess.getGuessedGender(), guess.getCreatedAt());
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/guess/DuplicateGuessException.java`:
```java
package com.genderreveal.api.guess;

public class DuplicateGuessException extends RuntimeException {
    public DuplicateGuessException(String slug) {
        super("Guest has already guessed on page: " + slug);
    }
}
```

`gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessExceptionHandler.java`:
```java
package com.genderreveal.api.guess;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GuessExceptionHandler {

    @ExceptionHandler(DuplicateGuessException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateGuess(DuplicateGuessException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
```

- [ ] **Step 4: GuessService 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessService.java`:
```java
package com.genderreveal.api.guess;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageNotFoundException;
import com.genderreveal.api.page.PageNotOpenException;
import com.genderreveal.api.page.PageRepository;
import com.genderreveal.api.page.PageStatus;
import com.genderreveal.api.page.PageStatusCalculator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class GuessService {

    private final GuessRepository guessRepository;
    private final PageRepository pageRepository;
    private final PageStatusCalculator statusCalculator;
    private final Clock clock;

    public GuessService(GuessRepository guessRepository, PageRepository pageRepository,
                         PageStatusCalculator statusCalculator, Clock clock) {
        this.guessRepository = guessRepository;
        this.pageRepository = pageRepository;
        this.statusCalculator = statusCalculator;
        this.clock = clock;
    }

    public Guess create(String slug, String guestCookieId, String guessedGender) {
        Page page = pageRepository.findBySlug(slug)
            .orElseThrow(() -> new PageNotFoundException(slug));

        Instant now = Instant.now(clock);
        PageStatus status = statusCalculator.calculate(page, now);
        if (status != PageStatus.OPEN) {
            throw new PageNotOpenException(slug);
        }

        if (guessRepository.existsByPageIdAndGuestCookieId(page.getId(), guestCookieId)) {
            throw new DuplicateGuessException(slug);
        }

        Guess guess = new Guess(page.getId(), guestCookieId, guessedGender, now);
        try {
            return guessRepository.save(guess);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateGuessException(slug);
        }
    }
}
```

- [ ] **Step 5: GuessController 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/guess/GuessController.java`:
```java
package com.genderreveal.api.guess;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/pages/{slug}/guesses")
public class GuessController {

    private static final String COOKIE_NAME = "guest_id";

    private final GuessService guessService;

    public GuessController(GuessService guessService) {
        this.guessService = guessService;
    }

    @PostMapping
    public ResponseEntity<GuessResponse> create(
            @PathVariable String slug,
            @CookieValue(name = COOKIE_NAME, required = false) String existingGuestId,
            @Valid @RequestBody GuessCreateRequest request) {

        String guestId = (existingGuestId != null && !existingGuestId.isBlank())
            ? existingGuestId
            : UUID.randomUUID().toString();

        Guess guess = guessService.create(slug, guestId, request.guessedGender());

        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, guestId)
            .path("/")
            .maxAge(Duration.ofDays(365))
            .httpOnly(true)
            .sameSite("Lax")
            .build();

        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(GuessResponse.from(guess));
    }
}
```

- [ ] **Step 6: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guess.GuessControllerTest"`
예상 결과: PASS (4개 테스트 모두)

- [ ] **Step 7: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/guess/
git add api/src/test/java/com/genderreveal/api/guess/GuessControllerTest.java
git commit -m "feat(api): 맞추기 API(POST /api/pages/{slug}/guesses) 추가"
```

---

### Task 5: 방명록 조회 API (`GET /api/pages/{slug}/guestbook`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryResponse.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryService.java`
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryController.java`
- Create: `gender-reveal/api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java`

**Interfaces:**
- Consumes: `GuestbookEntryRepository`(Task 2), `com.genderreveal.api.page.PageRepository`/`PageStatusCalculator`/`PageStatus`/`PageNotFoundException`/`PageNotOpenException`
- Produces: `GET /api/pages/{slug}/guestbook` → 200 + `List<GuestbookEntryResponse>` | 404 | 409, `GuestbookEntryService.list(String): List<GuestbookEntry>`

이 태스크와 Task 6(등록)은 같은 컨트롤러/서비스 파일을 함께 채우는 것이 자연스러워 Task 6에서 이어서 같은 파일들을 수정한다. Task 5는 조회만, Task 6은 등록을 추가한다.

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성 (조회 부분만)**

`gender-reveal/api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java`:
```java
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
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guestbook.GuestbookEntryControllerTest"`
예상 결과: 컴파일 실패 — DTO/서비스/컨트롤러가 아직 없음

- [ ] **Step 3: DTO 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryResponse.java`:
```java
package com.genderreveal.api.guestbook;

import java.time.Instant;

public record GuestbookEntryResponse(String nickname, String message, Instant createdAt) {
    static GuestbookEntryResponse from(GuestbookEntry entry) {
        return new GuestbookEntryResponse(entry.getNickname(), entry.getMessage(), entry.getCreatedAt());
    }
}
```

- [ ] **Step 4: GuestbookEntryService 작성 (조회만)**

`gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryService.java`:
```java
package com.genderreveal.api.guestbook;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageNotFoundException;
import com.genderreveal.api.page.PageNotOpenException;
import com.genderreveal.api.page.PageRepository;
import com.genderreveal.api.page.PageStatus;
import com.genderreveal.api.page.PageStatusCalculator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class GuestbookEntryService {

    private final GuestbookEntryRepository guestbookEntryRepository;
    private final PageRepository pageRepository;
    private final PageStatusCalculator statusCalculator;
    private final Clock clock;

    public GuestbookEntryService(GuestbookEntryRepository guestbookEntryRepository, PageRepository pageRepository,
                                  PageStatusCalculator statusCalculator, Clock clock) {
        this.guestbookEntryRepository = guestbookEntryRepository;
        this.pageRepository = pageRepository;
        this.statusCalculator = statusCalculator;
        this.clock = clock;
    }

    public List<GuestbookEntry> list(String slug) {
        Page page = requireOpenPage(slug);
        return guestbookEntryRepository.findByPageIdAndHiddenFalseOrderByCreatedAtDesc(page.getId());
    }

    private Page requireOpenPage(String slug) {
        Page page = pageRepository.findBySlug(slug)
            .orElseThrow(() -> new PageNotFoundException(slug));

        PageStatus status = statusCalculator.calculate(page, Instant.now(clock));
        if (status != PageStatus.OPEN) {
            throw new PageNotOpenException(slug);
        }
        return page;
    }
}
```

- [ ] **Step 5: GuestbookEntryController 작성 (조회만)**

`gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryController.java`:
```java
package com.genderreveal.api.guestbook;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pages/{slug}/guestbook")
public class GuestbookEntryController {

    private final GuestbookEntryService guestbookEntryService;

    public GuestbookEntryController(GuestbookEntryService guestbookEntryService) {
        this.guestbookEntryService = guestbookEntryService;
    }

    @GetMapping
    public List<GuestbookEntryResponse> list(@PathVariable String slug) {
        return guestbookEntryService.list(slug).stream()
            .map(GuestbookEntryResponse::from)
            .toList();
    }
}
```

- [ ] **Step 6: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guestbook.GuestbookEntryControllerTest"`
예상 결과: PASS (2개 테스트 모두)

- [ ] **Step 7: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryResponse.java api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryService.java api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryController.java api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java
git commit -m "feat(api): 방명록 조회 API(GET /api/pages/{slug}/guestbook) 추가"
```

---

### Task 6: 방명록 등록 API (`POST /api/pages/{slug}/guestbook`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryCreateRequest.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryController.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java`

**Interfaces:**
- Produces: `POST /api/pages/{slug}/guestbook` → 201 + `GuestbookEntryResponse` | 404 | 409, `GuestbookEntryService.create(String, String, String): GuestbookEntry`

- [ ] **Step 1: 기존 테스트 파일에 등록 관련 테스트 추가**

`gender-reveal/api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java`에 아래 3개 테스트 메서드를 (기존 두 메서드 뒤에) 추가:
```java
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
            "ownerEmail", "owner@example.com",
            "slug", "guestbook-secret-slug"
        );
        mockMvc.perform(post("/api/pages")
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
```
(이 파일 상단 import에 `static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post`가 이미 있는지 확인 — Task 5에서 이미 추가했다.)

- [ ] **Step 2: 테스트 실행해 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guestbook.GuestbookEntryControllerTest"`
예상 결과: 컴파일 실패(POST 매핑/DTO 없음) 또는 405

- [ ] **Step 3: GuestbookEntryCreateRequest 작성**

`gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryCreateRequest.java`:
```java
package com.genderreveal.api.guestbook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestbookEntryCreateRequest(
    @NotBlank @Size(max = 40) String nickname,
    @NotBlank @Size(max = 500) String message
) {}
```

- [ ] **Step 4: GuestbookEntryService에 등록 메서드 추가**

`gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryService.java`의 `list` 메서드 뒤에 추가:
```java
    public GuestbookEntry create(String slug, String nickname, String message) {
        Page page = requireOpenPage(slug);
        GuestbookEntry entry = new GuestbookEntry(page.getId(), nickname, message, Instant.now(clock));
        return guestbookEntryRepository.save(entry);
    }
```

- [ ] **Step 5: GuestbookEntryController에 등록 엔드포인트 추가**

`gender-reveal/api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryController.java`에 다음 import를 추가:
```java
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
```
그리고 `list` 메서드 뒤에 다음 메서드를 추가:
```java
    @PostMapping
    public ResponseEntity<GuestbookEntryResponse> create(@PathVariable String slug,
                                                           @Valid @RequestBody GuestbookEntryCreateRequest request) {
        GuestbookEntry entry = guestbookEntryService.create(slug, request.nickname(), request.message());
        return ResponseEntity.status(HttpStatus.CREATED).body(GuestbookEntryResponse.from(entry));
    }
```

- [ ] **Step 6: 테스트 실행해 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.guestbook.GuestbookEntryControllerTest"`
예상 결과: PASS (5개 테스트 모두)

- [ ] **Step 7: 전체 스위트 실행**

실행: `cd gender-reveal/api && ./gradlew test`
예상 결과: 전체 PASS (Plan 1 + Task 1~6 전체, 회귀 없음)

- [ ] **Step 8: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryCreateRequest.java api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryService.java api/src/main/java/com/genderreveal/api/guestbook/GuestbookEntryController.java api/src/test/java/com/genderreveal/api/guestbook/GuestbookEntryControllerTest.java
git commit -m "feat(api): 방명록 등록 API(POST /api/pages/{slug}/guestbook) 추가"
```

---

## 이 계획 완료 후 상태

- 게스트는 `open` 상태의 페이지에서 1회 맞추기(쿠키 기반 중복 방지)와 방명록 작성/조회(제한 없음, 숨김 항목 제외)를 할 수 있다.
- `PageExceptionHandler`는 더 이상 범용 예외 타입을 잡지 않고, 각 기능이 자기 영역의 예외만 명확하게 처리한다 — 다음 기능(관리자 대시보드 등)이 안전하게 이어 붙을 수 있는 상태.
- 여전히 없는 것: 인증(Plan 3), 관리자용 숨김/노출/삭제 API(Plan 3/5), 프론트엔드(Plan 4).

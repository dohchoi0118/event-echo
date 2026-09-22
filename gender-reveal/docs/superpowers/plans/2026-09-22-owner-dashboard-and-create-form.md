# 젠더리빌 소유자 화면 구현 계획 (Plan 5): 로그인 · 대시보드 · 페이지 작성

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 소유자가 이메일 매직링크로 로그인하고, 자신의 페이지 목록·통계·방명록을 관리하고 보관주기를 연장하며, 새 페이지를 만들어 발행할 수 있는 화면을 만든다.

**Architecture:** `web/`(Plan 4에서 만든 Next.js 정적 export 프로젝트)에 소유자 전용 라우트 3개(`/login`, `/dashboard`, `/create`)를 추가한다. `/dashboard`는 `/g` 셸과 같은 패턴 — **하나의 정적 페이지가 클라이언트에서 분기**하되, 방문자 페이지와 달리 슬러그를 URL *경로*가 아니라 **쿼리 문자열**(`/dashboard?slug=...`)로 받는다. 쿼리 문자열은 nginx의 `try_files` 매칭에 영향을 주지 않으므로 — Plan 4가 `/g/<slug>`를 위해 추가한 정규식 location 규칙과 달리 — `/dashboard`에는 **nginx 규칙을 추가하지 않아도 된다**(정적 파일 `dashboard.html`이 그대로 서빙됨). 소유자 인증 상태는 `GET /api/auth/me` 호출로 판별하고, 401이면 `/login`으로 이동시킨다. 백엔드는 Plan 3에서 이미 완성돼 있고, 대시보드 상세 화면에 필요한 한 가지(소유자 자신에게 실제 성별을 보여주는 것)만 작은 조회 엔드포인트로 보강한다.

**Tech Stack:** Plan 4와 동일 (Next.js 15 정적 export, React 19, TypeScript, Tailwind 3.4, Vitest + RTL). 백엔드 보강은 Plan 1–3과 동일 (Spring Boot 3.3, Java 21).

**Spec:** [docs/superpowers/specs/2026-09-18-implementation-design.md](../specs/2026-09-18-implementation-design.md) 7절(프론트엔드 구조)의 `/dashboard/[slug]`, `/create` 항목이 아래 "스펙에서 바뀐 결정"으로 갱신된다(스펙 13절에도 기록). 화면 카피는 [planning/screens/project/Admin.dc.html](../../../planning/screens/project/Admin.dc.html) 목업을 최대한 따르되, `/login`·`/create`는 목업이 없어 이 계획에서 새로 카피를 정한다(아래 각 태스크에 명시).

## 스펙에서 바뀐 결정 (이 계획에서 내린 결정)

- **`/dashboard/[slug]`가 아니라 `/dashboard?slug=...`.** 방문자용 `/g/[slug]`는 공유되는 예쁜 URL이어야 해서 경로 세그먼트 + nginx 정규식 라우팅을 썼지만(Plan 4), 대시보드 URL은 공유되지 않으므로 쿼리 문자열로 충분하고 nginx 변경이 필요 없다. `/dashboard`(쿼리 없음) = 내 페이지 목록, `/dashboard?slug=x` = 그 페이지의 상세(통계·방명록 관리·보관주기)를 같은 페이지 컴포넌트가 분기해서 보여준다.
- **소유자 대시보드에 "실제 성별" 노출용 조회 엔드포인트를 하나 추가한다.** 기존 소유자 API(`OwnerPageSummary`, `PageStats`, 방명록 목록)는 실제 성별(`actualGender`)을 어디에도 반환하지 않는다 — 소유자 자신이 입력한 값인데도 대시보드에서 다시 확인할 방법이 없다. `GET /api/owner/pages/{slug}`(`OwnerPageDetail`)를 추가해 페이지 설정 전체(닉네임, 실제 성별, 공개 예정 일시, 출산예정일, 문구, 테마, 상태, 만료일, 연장 여부)를 보여준다.

## 이 계획에서 스펙을 넘어 내린 설계 판단

- **공개 예정 일시 수정 기능은 이번 범위에서 제외한다.** Requirements.md 4절이 "공개 예정 일시 수정"을 언급하지만, 이미 게스트가 방문했거나 페이지가 열린 뒤 시각을 바꾸는 것의 의미(맞추기 결과가 뒤섞임 등)가 정의돼 있지 않다. Admin 목업도 "공개 예정 일시"를 **표시만** 하고 편집 UI는 없다. 대시보드는 조회만 제공하고, 수정은 후속 계획으로 미룬다.
- **미리보기는 애니메이션 재현이 아니라 입력값 요약이다.** Requirements.md는 "실제 결과 화면과 동일하게 보이는 미리보기"를 요구하지만, 방문자용 리빌 연출(Plan 4)을 작성 폼에서 그대로 재사용하려면 실제 성별을 입력값에서 가져와야 해서 작성자 본인에게는 자연스럽지만 구현 범위가 커진다. 1차는 텍스트/아이콘 요약 카드(닉네임, 성별, 띠, 테마, 문구)로 미리보기를 제공하고, "발행 후 실제 화면에서 확인해 보세요" 안내로 대신한다. 애니메이션 미리보기는 후속 과제로 남긴다.
- **BGM 토글은 폼에 없다.** Plan 4가 BGM을 1차 범위에서 제외했으므로 작성 폼도 BGM 관련 입력을 두지 않고, `POST /api/pages` 요청에는 항상 `bgmEnabled: false`를 보낸다.
- **슬러그 중복 확인은 별도 API를 만들지 않는다.** `POST /api/pages`가 이미 슬러그 충돌 시 409를 준다. 작성 폼은 제출 시 409를 받으면 슬러그 입력창 아래에 인라인 오류로 보여준다("이미 사용 중인 주소예요").
- **매직링크 요청은 이메일 존재 여부·성공 여부를 UI에서 구분하지 않는다**(백엔드가 항상 202를 주는 것과 동일한 이유 — 계정 열거 방지). 요청 후에는 항상 "메일함을 확인해 주세요" 화면을 보여준다.
- **소유자 페이지는 세션 확인을 각 화면이 직접 한다** (`useOwnerSession` 훅으로 `GET /api/auth/me` 호출). `/dashboard`, `/create`는 미인증이면 `/login`으로 이동하고, `/login`은 이미 인증돼 있으면 `/dashboard`로 이동한다. 클라이언트 라우팅 프레임워크 없이 `window.location.href` 전체 이동으로 처리한다(Plan 4의 `/g`와 같은 스타일).
- **CSRF는 별도 토큰 없이 `SameSite=Lax` 쿠키에 의존한다**(Plan 3에서 이미 결정됨). `owner_session`이 SameSite=Lax이므로 다른 출처의 크로스사이트 POST/PATCH/DELETE에는 쿠키가 실리지 않는다. 추가 조치는 하지 않는다.

## Global Constraints

- 백엔드 작업(Task 1)은 `gender-reveal/api`에서만, 프론트 작업(Task 2–9)은 `gender-reveal/web`에서만 한다. Task 1 외에는 `api/`를 건드리지 않는다.
- 프론트 전역 제약은 Plan 4의 Global Constraints를 그대로 따른다: 정적 export 호환(서버 전용 기능 금지), API 호출은 전부 `src/lib/api.ts`를 거침(`credentials: 'same-origin'`, GET은 `cache: 'no-store'`), 방명록 메시지/닉네임은 항상 텍스트 자식으로만 렌더(이번 계획도 동일 — 소유자 화면의 방명록 관리 테이블 포함), 색 사용 원칙(대시보드·로그인·작성 화면은 소유자용이라 `accent-boy`/`accent-girl`을 성별 강조로 쓰지 않는다 — 실제 성별은 텍스트로만 보여준다. 작성 폼의 테마 선택 카드에서도 색상 강조를 쓰지 않는다), display 폰트는 타이틀에만.
- 백엔드 제약(Plan 1–3과 동일): 시간은 주입된 `Clock`, `Instant` 컬럼은 `InstantStringConverter`, 도메인 예외는 전용 타입, `Cache-Control: no-store`, 남의 페이지는 404.
- 소유자 화면(`/dashboard`, `/create`)은 `GET /api/auth/me`가 401이면 `/login`으로 이동한다. `/login`은 이미 세션이 있으면 `/dashboard`로 이동한다.
- 테스트는 Vitest + React Testing Library. 각 태스크는 TDD. 전체 스위트(`npm test`, 백엔드는 `./gradlew test`)와 `npm run build`가 통과해야 한다.

## 파일 구조 요약

```
api/src/main/java/com/genderreveal/api/owner/
  OwnerPageDetail.java                (신규)
  OwnerPageService.java               (수정 — detail 메서드 추가)
  OwnerPageController.java            (수정 — GET /{slug} 추가)
api/src/test/java/com/genderreveal/api/owner/
  OwnerPageControllerTest.java        (수정 — detail 테스트 추가)

web/src/
  lib/
    types.ts     (수정 — 소유자 관련 타입 추가)
    api.ts       (수정 — 소유자 API 함수 추가)
  hooks/
    useOwnerSession.ts                (신규)
  components/owner/
    OwnerPageListScreen.tsx           (신규)
    OwnerPageDetailScreen.tsx         (신규)
    GuestbookModerationTable.tsx      (신규)
    CreatePageForm.tsx                (신규)
    CreatePagePreview.tsx             (신규)
    PublishSuccessScreen.tsx          (신규)
  app/
    login/page.tsx                   (신규)
    dashboard/page.tsx                (신규)
    create/page.tsx                   (신규)
web/nginx/gender-reveal.conf          (수정 — Plan 4의 TODO 주석 제거)
web/README.md                        (수정 — 로그인 플로우 개발 안내 추가)
```

---

### Task 1: 백엔드 — 소유자 페이지 상세 조회 (`GET /api/owner/pages/{slug}`)

**Files:**
- Create: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageDetail.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageService.java`
- Modify: `gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageController.java`
- Modify: `gender-reveal/api/src/test/java/com/genderreveal/api/owner/OwnerPageControllerTest.java`

**Interfaces:**
- Produces: `record OwnerPageDetail(String slug, String nickname, String actualGender, LocalDate dueDate, String message, String theme, boolean bgmEnabled, String status, Instant revealAt, Instant createdAt, Instant expiresAt, boolean extended)`, `OwnerPageService.detail(String slug, String ownerEmail): OwnerPageDetail`, `GET /api/owner/pages/{slug}` → 200 + `OwnerPageDetail` | 401 | 404(없음/남의 것)

- [ ] **Step 1: 실패하는 테스트 작성**

`OwnerPageControllerTest.java`에 테스트를 추가한다 (클래스에 이미 `pageRepository`, `ownerTestSupport`, `mockMvc`가 있으므로 재사용; `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;`가 없으면 추가):
```java
    @Test
    void detailExposesActualGenderAndSettingsToTheOwner() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        pageRepository.save(new Page(
            "detail-slug", "뽀튼이", "girl", now.plus(2, ChronoUnit.HOURS),
            LocalDate.of(2026, 11, 3), "환영해요", "cake", false,
            "detail-owner@example.com", now, now.plus(30, ChronoUnit.DAYS)));

        mockMvc.perform(get("/api/owner/pages/detail-slug")
                .cookie(ownerTestSupport.cookieFor("detail-owner@example.com")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.slug").value("detail-slug"))
            .andExpect(jsonPath("$.nickname").value("뽀튼이"))
            .andExpect(jsonPath("$.actualGender").value("girl"))
            .andExpect(jsonPath("$.dueDate").value("2026-11-03"))
            .andExpect(jsonPath("$.message").value("환영해요"))
            .andExpect(jsonPath("$.theme").value("cake"))
            .andExpect(jsonPath("$.bgmEnabled").value(false))
            .andExpect(jsonPath("$.status").value("secret"))
            .andExpect(jsonPath("$.extended").value(false));
    }

    @Test
    void detailOfSomeoneElsesPageOrMissingSlugIs404() throws Exception {
        pageRepository.save(new Page(
            "detail-private-slug", "뽀튼이", "boy", Instant.now().minus(1, ChronoUnit.HOURS),
            null, null, "box", false, "victim@example.com",
            Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS)));

        mockMvc.perform(get("/api/owner/pages/detail-private-slug")
                .cookie(ownerTestSupport.cookieFor("intruder@example.com")))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/owner/pages/no-such-slug")
                .cookie(ownerTestSupport.cookieFor("intruder@example.com")))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/owner/pages/detail-private-slug"))
            .andExpect(status().isUnauthorized());
    }
```
(`import java.time.LocalDate;`가 없으면 추가.)

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/api && ./gradlew test --tests "com.genderreveal.api.owner.OwnerPageControllerTest"`
예상: 404(엔드포인트 없음)로 실패

- [ ] **Step 3: 구현**

`gender-reveal/api/src/main/java/com/genderreveal/api/owner/OwnerPageDetail.java`:
```java
package com.genderreveal.api.owner;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

public record OwnerPageDetail(String slug, String nickname, String actualGender, LocalDate dueDate,
                               String message, String theme, boolean bgmEnabled, String status,
                               Instant revealAt, Instant createdAt, Instant expiresAt, boolean extended) {

    static OwnerPageDetail of(Page page, PageStatus status) {
        return new OwnerPageDetail(page.getSlug(), page.getNickname(), page.getActualGender(), page.getDueDate(),
            page.getMessage(), page.getTheme(), page.isBgmEnabled(), status.name().toLowerCase(Locale.ROOT),
            page.getRevealAt(), page.getCreatedAt(), page.getExpiresAt(), page.isExtended());
    }
}
```

`OwnerPageService.java`에 메서드를 추가한다 (`requireOwned`, `statusCalculator`, `clock`은 이미 필드로 있다):
```java
    public OwnerPageDetail detail(String slug, String ownerEmail) {
        Page page = requireOwned(slug, ownerEmail);
        return OwnerPageDetail.of(page, statusCalculator.calculate(page, Instant.now(clock)));
    }
```

`OwnerPageController.java`에 추가한다:
```java
    @GetMapping("/{slug}")
    public ResponseEntity<OwnerPageDetail> detail(OwnerPrincipal owner, @PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ownerPageService.detail(slug, owner.email()));
    }
```
주의: `@GetMapping("/{slug}/stats")`와 `@GetMapping("/{slug}/extend")`(POST)가 이미 있으므로 경로 충돌은 없다. 메서드 순서는 상관없다.

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/api && ./gradlew test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add api/src/main/java/com/genderreveal/api/owner/ api/src/test/java/com/genderreveal/api/owner/OwnerPageControllerTest.java
git commit -m "feat(api): 소유자 페이지 상세 조회 API 추가(실제 성별 등 전체 설정 노출)"
```

---

### Task 2: 프론트 — 소유자 API 클라이언트와 타입, 세션 훅

**Files:**
- Modify: `gender-reveal/web/src/lib/types.ts`
- Modify: `gender-reveal/web/src/lib/api.ts`
- Create: `gender-reveal/web/src/hooks/useOwnerSession.ts`
- Modify: `gender-reveal/web/src/lib/api.test.ts`
- Create: `gender-reveal/web/src/hooks/useOwnerSession.test.ts`

**Interfaces:**
- Consumes: Task 1의 `OwnerPageDetail` 응답 형태, Plan 4의 `request`/`ApiError`/`pagePath` 패턴(`src/lib/api.ts` 내부 유지)
- Produces: 타입 `OwnerPageSummary`, `OwnerPageDetail`, `PageStats`, `OwnerGuestbookEntry`, `PageCreatePayload`; 함수 `requestMagicLink(email)`, `getMe(): Promise<{email:string}>`, `logout()`, `listOwnerPages()`, `getOwnerPageDetail(slug)`, `getOwnerStats(slug)`, `getOwnerGuestbook(slug)`, `setGuestbookHidden(slug, entryId, hidden)`, `deleteGuestbookEntry(slug, entryId)`, `extendPage(slug)`, `createPage(payload): Promise<{slug:string}>`; 훅 `useOwnerSession(): {status: 'loading'|'authenticated'|'unauthenticated'; email: string|null}`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/lib/api.test.ts`에 다음을 추가한다 (기존 `respond`/`fetchMock`/`beforeEach`/`afterEach`를 재사용; 새 import는 파일 상단의 기존 import 줄에 이름만 추가):
```ts
describe('owner auth calls', () => {
  it('requests a magic link', async () => {
    fetchMock.mockReturnValue(respond(202));

    await requestMagicLink('owner@example.com');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/auth/magic-link');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ email: 'owner@example.com' });
  });

  it('gets the current owner', async () => {
    fetchMock.mockReturnValue(respond(200, { email: 'owner@example.com' }));

    expect(await getMe()).toEqual({ email: 'owner@example.com' });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/me');
  });

  it('propagates 401 from getMe as ApiError', async () => {
    fetchMock.mockReturnValue(respond(401, { error: 'Unauthorized' }));

    await expect(getMe()).rejects.toMatchObject({ status: 401 });
  });

  it('logs out', async () => {
    fetchMock.mockReturnValue(respond(204));

    await logout();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/auth/logout');
    expect(init.method).toBe('POST');
  });
});

describe('owner page calls', () => {
  it('lists owner pages', async () => {
    const pages = [{ slug: 's', nickname: '뽀', status: 'open', revealAt: 'x', expiresAt: 'y', extended: false, theme: 'box' }];
    fetchMock.mockReturnValue(respond(200, pages));

    expect(await listOwnerPages()).toEqual(pages);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/owner/pages');
  });

  it('gets owner page detail', async () => {
    const detail = { slug: 's', nickname: '뽀', actualGender: 'boy', dueDate: null, message: null, theme: 'box', bgmEnabled: false, status: 'open', revealAt: 'x', createdAt: 'y', expiresAt: 'z', extended: false };
    fetchMock.mockReturnValue(respond(200, detail));

    expect(await getOwnerPageDetail('s')).toEqual(detail);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/owner/pages/s');
  });

  it('gets owner stats', async () => {
    const stats = { visitors: 1, guessers: 1, boyGuesses: 1, girlGuesses: 0 };
    fetchMock.mockReturnValue(respond(200, stats));

    expect(await getOwnerStats('s')).toEqual(stats);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/owner/pages/s/stats');
  });

  it('extends a page', async () => {
    fetchMock.mockReturnValue(respond(200, { slug: 's', nickname: '뽀', status: 'open', revealAt: 'x', expiresAt: 'y', extended: true, theme: 'box' }));

    const result = await extendPage('s');

    expect(result.extended).toBe(true);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/owner/pages/s/extend');
    expect(init.method).toBe('POST');
  });

  it('creates a page', async () => {
    fetchMock.mockReturnValue(respond(201, { slug: 'new-slug' }));

    const payload = {
      nickname: '뽀튼이', actualGender: 'boy' as const, revealAt: '2026-10-01T00:00:00.000Z',
      dueDate: null, message: null, theme: 'box' as const, bgmEnabled: false, slug: undefined,
    };
    const result = await createPage(payload);

    expect(result).toEqual({ slug: 'new-slug' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/pages');
    expect(JSON.parse(init.body)).toEqual(payload);
  });
});

describe('owner guestbook moderation calls', () => {
  it('lists owner guestbook entries', async () => {
    const entries = [{ id: 1, nickname: '이모', message: '축하', createdAt: 'x', hidden: false }];
    fetchMock.mockReturnValue(respond(200, entries));

    expect(await getOwnerGuestbook('s')).toEqual(entries);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/owner/pages/s/guestbook');
  });

  it('sets an entry hidden', async () => {
    fetchMock.mockReturnValue(respond(204));

    await setGuestbookHidden('s', 1, true);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/owner/pages/s/guestbook/1');
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(init.body)).toEqual({ hidden: true });
  });

  it('deletes an entry', async () => {
    fetchMock.mockReturnValue(respond(204));

    await deleteGuestbookEntry('s', 1);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/owner/pages/s/guestbook/1');
    expect(init.method).toBe('DELETE');
  });
});
```
파일 맨 위 import 줄에 다음 이름들을 추가한다: `requestMagicLink, getMe, logout, listOwnerPages, getOwnerPageDetail, getOwnerStats, getOwnerGuestbook, setGuestbookHidden, deleteGuestbookEntry, extendPage, createPage`.

`gender-reveal/web/src/hooks/useOwnerSession.test.ts`:
```ts
import { renderHook, waitFor } from '@testing-library/react';
import { useOwnerSession } from './useOwnerSession';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getMe: vi.fn() };
});

const getMe = vi.mocked(api.getMe);

beforeEach(() => getMe.mockReset());

describe('useOwnerSession', () => {
  it('starts loading, then reports the authenticated email', async () => {
    getMe.mockResolvedValue({ email: 'owner@example.com' });

    const { result } = renderHook(() => useOwnerSession());

    expect(result.current.status).toBe('loading');
    await waitFor(() => expect(result.current.status).toBe('authenticated'));
    expect(result.current.email).toBe('owner@example.com');
  });

  it('reports unauthenticated on a 401', async () => {
    getMe.mockRejectedValue(new api.ApiError(401, { error: 'Unauthorized' }));

    const { result } = renderHook(() => useOwnerSession());

    await waitFor(() => expect(result.current.status).toBe('unauthenticated'));
    expect(result.current.email).toBeNull();
  });

  it('treats any other failure as unauthenticated too (fail closed)', async () => {
    getMe.mockRejectedValue(new api.ApiError(500, null));

    const { result } = renderHook(() => useOwnerSession());

    await waitFor(() => expect(result.current.status).toBe('unauthenticated'));
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈/함수 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/lib/types.ts`에 추가 (기존 타입들 아래):
```ts
export type OwnerPageSummary = {
  slug: string;
  nickname: string;
  status: 'secret' | 'open' | 'expired';
  revealAt: string;
  expiresAt: string;
  extended: boolean;
  theme: Theme;
};

export type OwnerPageDetail = {
  slug: string;
  nickname: string;
  actualGender: Gender;
  dueDate: string | null;
  message: string | null;
  theme: Theme;
  bgmEnabled: boolean;
  status: 'secret' | 'open' | 'expired';
  revealAt: string;
  createdAt: string;
  expiresAt: string;
  extended: boolean;
};

export type PageStats = {
  visitors: number;
  guessers: number;
  boyGuesses: number;
  girlGuesses: number;
};

export type OwnerGuestbookEntry = {
  id: number;
  nickname: string;
  message: string;
  createdAt: string;
  hidden: boolean;
  guessedGender?: Gender;
  guessCorrect?: boolean;
};

export type PageCreatePayload = {
  nickname: string;
  actualGender: Gender;
  revealAt: string;
  dueDate: string | null;
  message: string | null;
  theme: Theme;
  bgmEnabled: boolean;
  slug?: string;
};
```

`gender-reveal/web/src/lib/api.ts`에 import와 함수를 추가한다 (기존 `import { parsePageView } from './types';` 줄을 아래로 교체):
```ts
import { parsePageView } from './types';
import type {
  Gender, GuestbookEntry, OwnerGuestbookEntry, OwnerPageDetail, OwnerPageSummary,
  PageCreatePayload, PageStats, PageView,
} from './types';
```
파일 끝에 추가:
```ts
export function requestMagicLink(email: string): Promise<void> {
  return postJson<void>('/api/auth/magic-link', { email });
}

export function getMe(): Promise<{ email: string }> {
  return request<{ email: string }>('/api/auth/me', { cache: 'no-store' });
}

export function logout(): Promise<void> {
  return postJson<void>('/api/auth/logout', undefined);
}

export function listOwnerPages(): Promise<OwnerPageSummary[]> {
  return request<OwnerPageSummary[]>('/api/owner/pages', { cache: 'no-store' });
}

export function getOwnerPageDetail(slug: string): Promise<OwnerPageDetail> {
  return request<OwnerPageDetail>(`/api/owner/pages/${encodeURIComponent(slug)}`, { cache: 'no-store' });
}

export function getOwnerStats(slug: string): Promise<PageStats> {
  return request<PageStats>(`/api/owner/pages/${encodeURIComponent(slug)}/stats`, { cache: 'no-store' });
}

export function extendPage(slug: string): Promise<OwnerPageSummary> {
  return postJson<OwnerPageSummary>(`/api/owner/pages/${encodeURIComponent(slug)}/extend`, undefined);
}

export function createPage(payload: PageCreatePayload): Promise<{ slug: string }> {
  return postJson<{ slug: string }>('/api/pages', payload);
}

export function getOwnerGuestbook(slug: string): Promise<OwnerGuestbookEntry[]> {
  return request<OwnerGuestbookEntry[]>(`/api/owner/pages/${encodeURIComponent(slug)}/guestbook`, { cache: 'no-store' });
}

export function setGuestbookHidden(slug: string, entryId: number, hidden: boolean): Promise<void> {
  return request<void>(`/api/owner/pages/${encodeURIComponent(slug)}/guestbook/${entryId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ hidden }),
  });
}

export function deleteGuestbookEntry(slug: string, entryId: number): Promise<void> {
  return request<void>(`/api/owner/pages/${encodeURIComponent(slug)}/guestbook/${entryId}`, { method: 'DELETE' });
}
```
주의: `postJson`은 현재 `body: JSON.stringify(payload)`를 항상 만든다. `logout()`처럼 본문이 없는 POST를 위해 `postJson`의 `payload` 인자가 `undefined`일 때 `body`를 생략하도록 다음처럼 살짝 고친다 (`postJson` 정의를 이걸로 교체):
```ts
function postJson<T>(path: string, payload: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: payload === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
}
```
`request`는 응답 바디가 없는 204를 다룰 수 있어야 한다 — 현재 `return (await response.json()) as T;`는 빈 바디에서 예외를 던진다. `request`의 마지막 줄을 다음으로 바꾼다:
```ts
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
```

`gender-reveal/web/src/hooks/useOwnerSession.ts`:
```ts
'use client';

import { useEffect, useState } from 'react';
import { getMe } from '@/lib/api';

type SessionState = { status: 'loading' | 'authenticated' | 'unauthenticated'; email: string | null };

export function useOwnerSession(): SessionState {
  const [state, setState] = useState<SessionState>({ status: 'loading', email: null });

  useEffect(() => {
    let cancelled = false;
    getMe()
      .then(({ email }) => {
        if (!cancelled) setState({ status: 'authenticated', email });
      })
      .catch(() => {
        // Any failure (401, network, 5xx) is treated as "not logged in" — fail closed.
        if (!cancelled) setState({ status: 'unauthenticated', email: null });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/lib/types.ts web/src/lib/api.ts web/src/lib/api.test.ts web/src/hooks/useOwnerSession.ts web/src/hooks/useOwnerSession.test.ts
git commit -m "feat(web): 소유자 API 클라이언트, 타입, 세션 훅 추가"
```

---

### Task 3: `/login` 화면

**Files:**
- Create: `gender-reveal/web/src/app/login/page.tsx`
- Create: `gender-reveal/web/src/components/owner/LoginScreen.tsx`
- Create: `gender-reveal/web/src/components/owner/LoginScreen.test.tsx`

**Interfaces:**
- Consumes: `requestMagicLink`(Task 2), `useOwnerSession`(Task 2), `Screen`/`Button`(Plan 4)
- Produces: `<LoginScreen redirectIfAuthenticated? />`, 라우트 `/login`

**카피(새로 정함):** 제목 `소유자 로그인`, 부제 `이메일로 로그인 링크를 보내드려요`, 입력 라벨 `이메일`, 버튼 `로그인 링크 받기`, 전송 후 `메일함을 확인해 주세요` / `{email}로 로그인 링크를 보냈어요. 15분 동안 유효해요.`, 오류 배너(쿼리 `?error=invalid`일 때) `링크가 만료되었거나 이미 사용됐어요. 다시 요청해 주세요.`, 유효성 오류 `올바른 이메일을 입력해 주세요`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/components/owner/LoginScreen.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginScreen } from './LoginScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, requestMagicLink: vi.fn() };
});

const requestMagicLink = vi.mocked(api.requestMagicLink);

beforeEach(() => {
  requestMagicLink.mockReset();
  window.history.pushState({}, '', '/login');
});

describe('LoginScreen', () => {
  it('requests a magic link and shows the sent state', async () => {
    requestMagicLink.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<LoginScreen />);

    await user.type(screen.getByLabelText('이메일'), 'owner@example.com');
    await user.click(screen.getByRole('button', { name: '로그인 링크 받기' }));

    expect(requestMagicLink).toHaveBeenCalledWith('owner@example.com');
    expect(await screen.findByText('메일함을 확인해 주세요')).toBeInTheDocument();
    expect(screen.getByText('owner@example.com로 로그인 링크를 보냈어요. 15분 동안 유효해요.')).toBeInTheDocument();
  });

  it('rejects an obviously invalid email without calling the API', async () => {
    const user = userEvent.setup();
    render(<LoginScreen />);

    await user.type(screen.getByLabelText('이메일'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: '로그인 링크 받기' }));

    expect(screen.getByRole('alert')).toHaveTextContent('올바른 이메일을 입력해 주세요');
    expect(requestMagicLink).not.toHaveBeenCalled();
  });

  it('shows the sent state even if the request technically fails (no enumeration signal)', async () => {
    requestMagicLink.mockRejectedValue(new api.ApiError(500, null));
    const user = userEvent.setup();
    render(<LoginScreen />);

    await user.type(screen.getByLabelText('이메일'), 'owner@example.com');
    await user.click(screen.getByRole('button', { name: '로그인 링크 받기' }));

    expect(await screen.findByText('메일함을 확인해 주세요')).toBeInTheDocument();
  });

  it('shows an error banner when the URL carries ?error=invalid', () => {
    window.history.pushState({}, '', '/login?error=invalid');

    render(<LoginScreen />);

    expect(screen.getByRole('alert')).toHaveTextContent('링크가 만료되었거나 이미 사용됐어요');
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/components/owner/LoginScreen.tsx`:
```tsx
'use client';

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '../Button';
import { Screen } from '../Screen';
import { requestMagicLink } from '@/lib/api';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function LoginScreen() {
  const [email, setEmail] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [callbackError, setCallbackError] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setCallbackError(new URLSearchParams(window.location.search).get('error') === 'invalid');
  }, []);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const trimmed = email.trim();
    if (!EMAIL_PATTERN.test(trimmed)) {
      setValidationError('올바른 이메일을 입력해 주세요');
      return;
    }
    setValidationError(null);
    setSubmitting(true);
    try {
      await requestMagicLink(trimmed);
    } catch {
      // Intentionally ignored: the API always means to say "check your mailbox",
      // regardless of whether the address exists or the send actually succeeded.
    } finally {
      setSubmitting(false);
      setSentTo(trimmed);
    }
  };

  if (sentTo) {
    return (
      <Screen>
        <p className="font-display text-display-md">메일함을 확인해 주세요</p>
        <p className="text-body text-ink-muted">{sentTo}로 로그인 링크를 보냈어요. 15분 동안 유효해요.</p>
      </Screen>
    );
  }

  return (
    <Screen>
      <h1 className="font-display text-display-lg">소유자 로그인</h1>
      <p className="text-body text-ink-muted">이메일로 로그인 링크를 보내드려요</p>
      {callbackError && (
        <p role="alert" className="text-body text-danger">
          링크가 만료되었거나 이미 사용됐어요. 다시 요청해 주세요.
        </p>
      )}
      <form onSubmit={onSubmit} className="flex w-full flex-col gap-space-2">
        <label className="flex flex-col gap-space-1 text-label">
          이메일
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-radius-sm border border-border bg-surface-100 px-space-2 py-space-2 text-body outline-none focus:border-accent-primary"
          />
        </label>
        {validationError && (
          <p role="alert" className="text-body-sm text-danger">
            {validationError}
          </p>
        )}
        <Button type="submit" disabled={submitting}>
          로그인 링크 받기
        </Button>
      </form>
    </Screen>
  );
}
```

`gender-reveal/web/src/app/login/page.tsx`:
```tsx
import type { Metadata } from 'next';
import { LoginScreen } from '@/components/owner/LoginScreen';

export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function Page() {
  return <LoginScreen />;
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/app/login web/src/components/owner/LoginScreen.tsx web/src/components/owner/LoginScreen.test.tsx
git commit -m "feat(web): 소유자 로그인 화면(/login) 추가"
```

---

### Task 4: `/dashboard` 목록 화면

**Files:**
- Create: `gender-reveal/web/src/app/dashboard/page.tsx`
- Create: `gender-reveal/web/src/components/owner/OwnerPageListScreen.tsx`
- Create: `gender-reveal/web/src/components/owner/OwnerPageListScreen.test.tsx`

**Interfaces:**
- Consumes: `useOwnerSession`, `listOwnerPages`, `logout`(Task 2)
- Produces: `<OwnerPageListScreen />` — 미인증 시 `/login`으로 이동, 인증되면 목록을 보여줌; 목록 항목 클릭 시 `/dashboard?slug=...`로 이동; `새 페이지 만들기` 버튼 → `/create`; `로그아웃` 버튼

**카피:** 제목 `내 페이지`, 상단 이메일 표시, `로그아웃`, `새 페이지 만들기`, 빈 목록 `아직 만든 페이지가 없어요`, 각 항목에 상태 배지(`secret`→`공개 대기`, `open`→`공개 중`, `expired`→`만료됨`)

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/components/owner/OwnerPageListScreen.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OwnerPageListScreen } from './OwnerPageListScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getMe: vi.fn(), listOwnerPages: vi.fn(), logout: vi.fn() };
});

const getMe = vi.mocked(api.getMe);
const listOwnerPages = vi.mocked(api.listOwnerPages);
const logout = vi.mocked(api.logout);

let assignedHref = '';

beforeEach(() => {
  getMe.mockReset();
  listOwnerPages.mockReset();
  logout.mockReset();
  assignedHref = '';
  vi.spyOn(window, 'location', 'get').mockReturnValue({
    ...window.location,
    set href(v: string) { assignedHref = v; },
    get href() { return assignedHref; },
  } as unknown as Location);
});

afterEach(() => vi.restoreAllMocks());

describe('OwnerPageListScreen', () => {
  it('redirects to /login when not authenticated', async () => {
    getMe.mockRejectedValue(new api.ApiError(401, null));

    render(<OwnerPageListScreen />);

    await new Promise((r) => setTimeout(r, 0));
    expect(assignedHref).toBe('/login');
  });

  it('shows the owner email and their pages once authenticated', async () => {
    getMe.mockResolvedValue({ email: 'owner@example.com' });
    listOwnerPages.mockResolvedValue([
      { slug: 'a', nickname: '뽀튼이', status: 'open', revealAt: 'x', expiresAt: 'y', extended: false, theme: 'box' },
      { slug: 'b', nickname: '별튼이', status: 'secret', revealAt: 'x', expiresAt: 'y', extended: false, theme: 'cake' },
    ]);

    render(<OwnerPageListScreen />);

    expect(await screen.findByText('owner@example.com')).toBeInTheDocument();
    expect(screen.getByText('뽀튼이')).toBeInTheDocument();
    expect(screen.getByText('공개 중')).toBeInTheDocument();
    expect(screen.getByText('별튼이')).toBeInTheDocument();
    expect(screen.getByText('공개 대기')).toBeInTheDocument();
  });

  it('shows an empty state', async () => {
    getMe.mockResolvedValue({ email: 'owner@example.com' });
    listOwnerPages.mockResolvedValue([]);

    render(<OwnerPageListScreen />);

    expect(await screen.findByText('아직 만든 페이지가 없어요')).toBeInTheDocument();
  });

  it('navigates to the detail view on click, and to /create on the create button', async () => {
    getMe.mockResolvedValue({ email: 'owner@example.com' });
    listOwnerPages.mockResolvedValue([
      { slug: 'a', nickname: '뽀튼이', status: 'open', revealAt: 'x', expiresAt: 'y', extended: false, theme: 'box' },
    ]);
    const user = userEvent.setup();
    render(<OwnerPageListScreen />);

    await user.click(await screen.findByText('뽀튼이'));
    expect(assignedHref).toBe('/dashboard?slug=a');

    await user.click(screen.getByRole('button', { name: '새 페이지 만들기' }));
    expect(assignedHref).toBe('/create');
  });

  it('logs out and redirects to /login', async () => {
    getMe.mockResolvedValue({ email: 'owner@example.com' });
    listOwnerPages.mockResolvedValue([]);
    logout.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<OwnerPageListScreen />);

    await user.click(await screen.findByRole('button', { name: '로그아웃' }));

    expect(logout).toHaveBeenCalledTimes(1);
    expect(assignedHref).toBe('/login');
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/components/owner/OwnerPageListScreen.tsx`:
```tsx
'use client';

import { useEffect, useState } from 'react';
import { logout as apiLogout, listOwnerPages } from '@/lib/api';
import { useOwnerSession } from '@/hooks/useOwnerSession';
import type { OwnerPageSummary } from '@/lib/types';

const STATUS_LABEL: Record<OwnerPageSummary['status'], string> = {
  secret: '공개 대기',
  open: '공개 중',
  expired: '만료됨',
};

export function OwnerPageListScreen() {
  const session = useOwnerSession();
  const [pages, setPages] = useState<OwnerPageSummary[] | null>(null);

  useEffect(() => {
    if (session.status === 'unauthenticated') {
      window.location.href = '/login';
    }
  }, [session.status]);

  useEffect(() => {
    if (session.status === 'authenticated') {
      void listOwnerPages().then(setPages);
    }
  }, [session.status]);

  if (session.status !== 'authenticated') {
    return null;
  }

  const onLogout = async () => {
    await apiLogout();
    window.location.href = '/login';
  };

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[480px] flex-col gap-space-4 px-space-3 py-space-5">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-display-lg">내 페이지</h1>
          <p className="text-body-sm text-ink-muted">{session.email}</p>
        </div>
        <button type="button" onClick={onLogout} className="text-label text-ink-muted underline">
          로그아웃
        </button>
      </header>

      <button
        type="button"
        onClick={() => { window.location.href = '/create'; }}
        className="rounded-radius-full bg-accent-primary px-space-4 py-space-2 text-label text-surface-100"
      >
        새 페이지 만들기
      </button>

      {pages && pages.length === 0 && <p className="text-body text-ink-muted">아직 만든 페이지가 없어요</p>}

      <ul className="flex flex-col gap-space-2">
        {pages?.map((page) => (
          <li key={page.slug}>
            <button
              type="button"
              onClick={() => { window.location.href = `/dashboard?slug=${encodeURIComponent(page.slug)}`; }}
              className="flex w-full items-center justify-between rounded-radius-md border border-border bg-surface-100 p-space-3 text-left"
            >
              <span className="text-body-lg">{page.nickname}</span>
              <span className="rounded-radius-full bg-surface-200 px-space-2 py-space-1 text-label text-ink-muted">
                {STATUS_LABEL[page.status]}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </main>
  );
}
```

`gender-reveal/web/src/app/dashboard/page.tsx`(이 태스크에서는 목록만 — 상세 분기는 Task 5에서 이 파일을 수정한다):
```tsx
import type { Metadata } from 'next';
import { OwnerPageListScreen } from '@/components/owner/OwnerPageListScreen';

export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function Page() {
  return <OwnerPageListScreen />;
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/app/dashboard web/src/components/owner/OwnerPageListScreen.tsx web/src/components/owner/OwnerPageListScreen.test.tsx
git commit -m "feat(web): 소유자 대시보드 목록 화면(/dashboard) 추가"
```

---

### Task 5: `/dashboard?slug=` 상세 화면(헤더·통계·보관주기 연장)

**Files:**
- Create: `gender-reveal/web/src/components/owner/OwnerPageDetailScreen.tsx`
- Create: `gender-reveal/web/src/components/owner/OwnerPageDetailScreen.test.tsx`
- Modify: `gender-reveal/web/src/app/dashboard/page.tsx`

**Interfaces:**
- Consumes: `useOwnerSession`, `getOwnerPageDetail`, `getOwnerStats`, `extendPage`(Task 2)
- Produces: `slugFromSearch(search: string): string | null`, `<OwnerPageDetailScreen slug />`(헤더 + 통계 카드 + 보관주기 섹션; 방명록 관리는 Task 6에서 이 컴포넌트에 이어붙인다), `/dashboard`가 `?slug=`에 따라 목록/상세를 분기

**카피(Admin 목업 기준):** 제목 `{닉네임}의 페이지`, 뒤로가기 `← 내 페이지`, 통계 라벨 `방문자 수`/`맞추기 참여자 수`/`예측 비율 (남아 / 여아)`, 페이지 설정 섹션 제목 `페이지 설정 / 보관주기`, 필드 `공개 예정 일시`/`실제 성별`/`보관주기 만료일`/`연장 사용 여부`(`미사용 (1회 가능)` / `사용함`), 버튼 `30일 연장하기`(연장 완료 시 비활성 + `사용함`으로 표시), 연장 확인 문구 `한 번 연장하면 되돌릴 수 없어요. 30일을 연장할까요?`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/components/owner/OwnerPageDetailScreen.test.tsx`:
```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OwnerPageDetailScreen, slugFromSearch } from './OwnerPageDetailScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getOwnerPageDetail: vi.fn(), getOwnerStats: vi.fn(), extendPage: vi.fn() };
});

const getOwnerPageDetail = vi.mocked(api.getOwnerPageDetail);
const getOwnerStats = vi.mocked(api.getOwnerStats);
const extendPage = vi.mocked(api.extendPage);

const detail = {
  slug: 'my-slug', nickname: '뽀튼이', actualGender: 'girl' as const, dueDate: '2026-11-03',
  message: null, theme: 'box' as const, bgmEnabled: false, status: 'open' as const,
  revealAt: '2026-10-01T09:00:00.000Z', createdAt: '2026-09-01T00:00:00.000Z',
  expiresAt: '2026-10-31T00:00:00.000Z', extended: false,
};
const stats = { visitors: 128, guessers: 64, boyGuesses: 32, girlGuesses: 32 };

beforeEach(() => {
  getOwnerPageDetail.mockReset();
  getOwnerStats.mockReset();
  extendPage.mockReset();
});

describe('slugFromSearch', () => {
  it('reads slug from a query string', () => {
    expect(slugFromSearch('?slug=my-slug')).toBe('my-slug');
    expect(slugFromSearch('?slug=a%20b')).toBe('a b');
  });

  it('returns null when there is no slug', () => {
    expect(slugFromSearch('')).toBeNull();
    expect(slugFromSearch('?other=1')).toBeNull();
  });
});

describe('OwnerPageDetailScreen', () => {
  it('shows the header, actual gender and stats', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);

    render(<OwnerPageDetailScreen slug="my-slug" />);

    expect(await screen.findByText('뽀튼이의 페이지')).toBeInTheDocument();
    expect(screen.getByText('여아')).toBeInTheDocument();
    expect(screen.getByText('128')).toBeInTheDocument();
    expect(screen.getByText('64명')).toBeInTheDocument();
    expect(screen.getByText('32 / 32')).toBeInTheDocument();
  });

  it('shows extend as available and lets the owner extend once', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);
    extendPage.mockResolvedValue({ slug: 'my-slug', nickname: '뽀튼이', status: 'open', revealAt: 'x', expiresAt: 'y', extended: true, theme: 'box' });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    render(<OwnerPageDetailScreen slug="my-slug" />);

    expect(await screen.findByText('미사용 (1회 가능)')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '30일 연장하기' }));

    expect(window.confirm).toHaveBeenCalledWith('한 번 연장하면 되돌릴 수 없어요. 30일을 연장할까요?');
    await waitFor(() => expect(screen.getByText('사용함')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '30일 연장하기' })).not.toBeInTheDocument();
  });

  it('does not extend when the confirmation is declined', async () => {
    getOwnerPageDetail.mockResolvedValue(detail);
    getOwnerStats.mockResolvedValue(stats);
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    render(<OwnerPageDetailScreen slug="my-slug" />);

    await user.click(await screen.findByRole('button', { name: '30일 연장하기' }));

    expect(extendPage).not.toHaveBeenCalled();
  });

  it('already-extended pages show no extend button', async () => {
    getOwnerPageDetail.mockResolvedValue({ ...detail, extended: true });
    getOwnerStats.mockResolvedValue(stats);

    render(<OwnerPageDetailScreen slug="my-slug" />);

    expect(await screen.findByText('사용함')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '30일 연장하기' })).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/components/owner/OwnerPageDetailScreen.tsx`:
```tsx
'use client';

import { useCallback, useEffect, useState } from 'react';
import { extendPage, getOwnerPageDetail, getOwnerStats } from '@/lib/api';
import type { OwnerPageDetail, PageStats } from '@/lib/types';
import { genderKo } from '@/lib/result';

export function slugFromSearch(search: string): string | null {
  return new URLSearchParams(search).get('slug');
}

export function OwnerPageDetailScreen({ slug }: { slug: string }) {
  const [detail, setDetail] = useState<OwnerPageDetail | null>(null);
  const [stats, setStats] = useState<PageStats | null>(null);
  const [extending, setExtending] = useState(false);

  const load = useCallback(() => {
    void getOwnerPageDetail(slug).then(setDetail);
    void getOwnerStats(slug).then(setStats);
  }, [slug]);

  useEffect(() => load(), [load]);

  const onExtend = async () => {
    if (!window.confirm('한 번 연장하면 되돌릴 수 없어요. 30일을 연장할까요?')) {
      return;
    }
    setExtending(true);
    try {
      await extendPage(slug);
    } finally {
      setExtending(false);
    }
    load();
  };

  if (!detail || !stats) {
    return null;
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[480px] flex-col gap-space-4 px-space-3 py-space-5">
      <a href="/dashboard" className="text-label text-ink-muted">← 내 페이지</a>
      <h1 className="font-display text-display-lg">{detail.nickname}의 페이지</h1>

      <section className="grid grid-cols-3 gap-space-2">
        <StatCard label="방문자 수" value={String(stats.visitors)} />
        <StatCard label="맞추기 참여자 수" value={`${stats.guessers}명`} />
        <StatCard label="예측 비율 (남아 / 여아)" value={`${stats.boyGuesses} / ${stats.girlGuesses}`} />
      </section>

      <section className="flex flex-col gap-space-2 rounded-radius-md border border-border bg-surface-100 p-space-3">
        <h2 className="font-display text-display-md">페이지 설정 / 보관주기</h2>
        <Row label="실제 성별" value={genderKo(detail.actualGender)} />
        <Row label="공개 예정 일시" value={new Date(detail.revealAt).toLocaleString('ko-KR')} />
        <Row label="보관주기 만료일" value={new Date(detail.expiresAt).toLocaleDateString('ko-KR')} />
        <Row label="연장 사용 여부" value={detail.extended ? '사용함' : '미사용 (1회 가능)'} />
        {!detail.extended && (
          <button
            type="button"
            disabled={extending}
            onClick={onExtend}
            className="self-start rounded-radius-full bg-accent-primary px-space-3 py-space-1 text-label text-surface-100 disabled:opacity-50"
          >
            30일 연장하기
          </button>
        )}
      </section>
    </main>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-space-1 rounded-radius-md border border-border bg-surface-100 p-space-2 text-center">
      <span className="text-body-sm text-ink-muted">{label}</span>
      <span className="text-display-md font-display">{value}</span>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between text-body">
      <span className="text-ink-muted">{label}</span>
      <span>{value}</span>
    </div>
  );
}
```
주의: `genderKo`는 Plan 4의 `src/lib/result.ts`가 내보낸다(`'boy' → '남아'`, `'girl' → '여아'`). `StatCard`의 라벨 텍스트 크기가 3열 그리드에서 줄바꿈될 수 있는데, 이는 디자인 다듬기이지 이 태스크의 범위가 아니다.

`gender-reveal/web/src/app/dashboard/page.tsx`를 다음으로 교체(클라이언트에서 분기하는 셸):
```tsx
'use client';

import { useEffect, useState } from 'react';
import { OwnerPageDetailScreen, slugFromSearch } from '@/components/owner/OwnerPageDetailScreen';
import { OwnerPageListScreen } from '@/components/owner/OwnerPageListScreen';

export default function Page() {
  const [slug, setSlug] = useState<string | null | undefined>(undefined);

  useEffect(() => {
    setSlug(slugFromSearch(window.location.search));
  }, []);

  if (slug === undefined) {
    return null;
  }
  return slug ? <OwnerPageDetailScreen slug={slug} /> : <OwnerPageListScreen />;
}
```
`export const metadata`는 서버 컴포넌트에서만 쓸 수 있으므로, `'use client'`로 바꾼 이 파일에서는 제거한다(정적 export에는 `robots` 메타가 없어도 무방 — 소유자 페이지는 `<html>`에 노출 방지용 태그가 없어도 검색엔진에 의미 있게 노출될 콘텐츠가 없다).

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/app/dashboard/page.tsx web/src/components/owner/OwnerPageDetailScreen.tsx web/src/components/owner/OwnerPageDetailScreen.test.tsx
git commit -m "feat(web): 대시보드 상세 화면(헤더·통계·보관주기 연장) 추가"
```

---

### Task 6: 방명록 관리 (숨김/노출/삭제)

**Files:**
- Create: `gender-reveal/web/src/components/owner/GuestbookModerationTable.tsx`
- Create: `gender-reveal/web/src/components/owner/GuestbookModerationTable.test.tsx`
- Modify: `gender-reveal/web/src/components/owner/OwnerPageDetailScreen.tsx`

**Interfaces:**
- Consumes: `getOwnerGuestbook`, `setGuestbookHidden`, `deleteGuestbookEntry`(Task 2), `relativeTime`(Plan 4)
- Produces: `<GuestbookModerationTable slug nowMs? />`(닉네임/메시지/등록일/상태/관리 테이블), `OwnerPageDetailScreen`이 이 테이블을 렌더

**카피(Admin 목업 기준):** 섹션 제목 `방명록 관리`, 표 헤더 `닉네임`/`메시지`/`등록일`/`상태`/`관리`, 상태 배지 `노출`/`숨김`, 액션 버튼(노출 상태일 때) `숨김`, (숨김 상태일 때) `노출 전환`, 항상 `삭제`, 삭제 확인 `이 메시지를 삭제할까요? 되돌릴 수 없어요.`, 빈 목록 `아직 등록된 메시지가 없어요`, 게스트 예측 표시(맞추기한 작성자만) `예측: 남아 · 정답` / `예측: 여아 · 오답`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/components/owner/GuestbookModerationTable.test.tsx`:
```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GuestbookModerationTable } from './GuestbookModerationTable';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getOwnerGuestbook: vi.fn(), setGuestbookHidden: vi.fn(), deleteGuestbookEntry: vi.fn() };
});

const getOwnerGuestbook = vi.mocked(api.getOwnerGuestbook);
const setGuestbookHidden = vi.mocked(api.setGuestbookHidden);
const deleteGuestbookEntry = vi.mocked(api.deleteGuestbookEntry);

const NOW = Date.parse('2026-09-21T12:00:00.000Z');

beforeEach(() => {
  getOwnerGuestbook.mockReset();
  setGuestbookHidden.mockReset();
  deleteGuestbookEntry.mockReset();
});

describe('GuestbookModerationTable', () => {
  it('lists entries with guess badges and renders text literally', async () => {
    getOwnerGuestbook.mockResolvedValue([
      { id: 1, nickname: '이모', message: '<b>축하해요</b>', createdAt: new Date(NOW - 1000).toISOString(), hidden: false, guessedGender: 'boy', guessCorrect: true },
      { id: 2, nickname: '삼촌', message: '고생하셨어요', createdAt: new Date(NOW - 2000).toISOString(), hidden: true },
    ]);

    const { container } = render(<GuestbookModerationTable slug="s" nowMs={NOW} />);

    expect(await screen.findByText('이모')).toBeInTheDocument();
    expect(screen.getByText('<b>축하해요</b>')).toBeInTheDocument();
    expect(container.querySelector('b')).toBeNull();
    expect(screen.getByText('예측: 남아 · 정답')).toBeInTheDocument();
    expect(screen.getByText('삼촌')).toBeInTheDocument();
    expect(screen.getAllByText('노출')).toHaveLength(1);
    expect(screen.getAllByText('숨김')).toHaveLength(1);
  });

  it('shows an empty state', async () => {
    getOwnerGuestbook.mockResolvedValue([]);

    render(<GuestbookModerationTable slug="s" nowMs={NOW} />);

    expect(await screen.findByText('아직 등록된 메시지가 없어요')).toBeInTheDocument();
  });

  it('hides a visible entry and shows 노출 전환 afterwards', async () => {
    getOwnerGuestbook.mockResolvedValue([
      { id: 1, nickname: '이모', message: '축하해요', createdAt: new Date(NOW).toISOString(), hidden: false },
    ]);
    setGuestbookHidden.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<GuestbookModerationTable slug="s" nowMs={NOW} />);

    await user.click(await screen.findByRole('button', { name: '숨김' }));

    expect(setGuestbookHidden).toHaveBeenCalledWith('s', 1, true);
    await waitFor(() => expect(screen.getByRole('button', { name: '노출 전환' })).toBeInTheDocument());
  });

  it('deletes an entry after confirmation and removes it from the list', async () => {
    getOwnerGuestbook.mockResolvedValue([
      { id: 1, nickname: '스팸', message: '광고', createdAt: new Date(NOW).toISOString(), hidden: false },
    ]);
    deleteGuestbookEntry.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    render(<GuestbookModerationTable slug="s" nowMs={NOW} />);

    await user.click(await screen.findByRole('button', { name: '삭제' }));

    expect(window.confirm).toHaveBeenCalledWith('이 메시지를 삭제할까요? 되돌릴 수 없어요.');
    expect(deleteGuestbookEntry).toHaveBeenCalledWith('s', 1);
    await waitFor(() => expect(screen.queryByText('스팸')).not.toBeInTheDocument());
  });

  it('does not delete when confirmation is declined', async () => {
    getOwnerGuestbook.mockResolvedValue([
      { id: 1, nickname: '스팸', message: '광고', createdAt: new Date(NOW).toISOString(), hidden: false },
    ]);
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    render(<GuestbookModerationTable slug="s" nowMs={NOW} />);

    await user.click(await screen.findByRole('button', { name: '삭제' }));

    expect(deleteGuestbookEntry).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/components/owner/GuestbookModerationTable.tsx`:
```tsx
'use client';

import { useCallback, useEffect, useState } from 'react';
import { deleteGuestbookEntry, getOwnerGuestbook, setGuestbookHidden } from '@/lib/api';
import { relativeTime } from '@/lib/time';
import { genderKo } from '@/lib/result';
import type { OwnerGuestbookEntry } from '@/lib/types';

export function GuestbookModerationTable({ slug, nowMs }: { slug: string; nowMs?: number }) {
  const [entries, setEntries] = useState<OwnerGuestbookEntry[] | null>(null);

  const load = useCallback(() => {
    void getOwnerGuestbook(slug).then(setEntries);
  }, [slug]);

  useEffect(() => load(), [load]);

  const onToggleHidden = async (entry: OwnerGuestbookEntry) => {
    await setGuestbookHidden(slug, entry.id, !entry.hidden);
    load();
  };

  const onDelete = async (entry: OwnerGuestbookEntry) => {
    if (!window.confirm('이 메시지를 삭제할까요? 되돌릴 수 없어요.')) {
      return;
    }
    await deleteGuestbookEntry(slug, entry.id);
    load();
  };

  const now = nowMs ?? Date.now();

  return (
    <section className="flex flex-col gap-space-2 rounded-radius-md border border-border bg-surface-100 p-space-3">
      <h2 className="font-display text-display-md">방명록 관리</h2>
      {entries && entries.length === 0 && <p className="text-body text-ink-muted">아직 등록된 메시지가 없어요</p>}
      <ul className="flex flex-col gap-space-2">
        {entries?.map((entry) => (
          <li key={entry.id} className="flex flex-col gap-space-1 rounded-radius-sm border border-border p-space-2">
            <div className="flex items-center justify-between">
              <span className="text-label">{entry.nickname}</span>
              <span className="rounded-radius-full bg-surface-200 px-space-2 py-space-1 text-body-sm text-ink-muted">
                {entry.hidden ? '숨김' : '노출'}
              </span>
            </div>
            <p className="whitespace-pre-line break-words text-body">{entry.message}</p>
            {entry.guessedGender && (
              <p className="text-body-sm text-ink-muted">
                예측: {genderKo(entry.guessedGender)} · {entry.guessCorrect ? '정답' : '오답'}
              </p>
            )}
            <div className="flex items-center justify-between text-body-sm text-ink-muted">
              <span>{relativeTime(entry.createdAt, now)}</span>
              <div className="flex gap-space-2">
                <button type="button" onClick={() => onToggleHidden(entry)} className="underline">
                  {entry.hidden ? '노출 전환' : '숨김'}
                </button>
                <button type="button" onClick={() => onDelete(entry)} className="text-danger underline">
                  삭제
                </button>
              </div>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
```

`OwnerPageDetailScreen.tsx`에서 import를 추가하고(`import { GuestbookModerationTable } from './GuestbookModerationTable';`), 페이지 설정 `<section>` 바로 아래에 `<GuestbookModerationTable slug={slug} />`를 추가한다.

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/components/owner/GuestbookModerationTable.tsx web/src/components/owner/GuestbookModerationTable.test.tsx web/src/components/owner/OwnerPageDetailScreen.tsx
git commit -m "feat(web): 방명록 관리 테이블(숨김/노출/삭제, 작성자 맞추기 표시) 추가"
```

---

### Task 7: `/create` 작성 폼

**Files:**
- Create: `gender-reveal/web/src/app/create/page.tsx`
- Create: `gender-reveal/web/src/components/owner/CreatePageForm.tsx`
- Create: `gender-reveal/web/src/components/owner/CreatePageForm.test.tsx`

**Interfaces:**
- Consumes: `useOwnerSession`(Task 2), `REVEAL_PROMPT`/테마 목록(Plan 4의 `src/lib/result.ts`), `Button`/`Screen`(Plan 4)
- Produces: `<CreatePageForm onSubmit={(payload: PageCreatePayload) => void} submitting error />`(입력만 담당, 실제 제출·미리보기 전환은 Task 8의 부모 컴포넌트가 함), 라우트 `/create`(미인증 시 `/login`으로 이동)

**카피(새로 정함):** 제목 `페이지 만들기`, 라벨 `태명`/`실제 성별`/`공개 예정 일시`/`출산 예정일 (선택)`/`축하 메시지 (선택)`/`리빌 테마`/`커스텀 주소 (선택)`, 성별 라디오 `남아`/`여아`, 테마 카드 `서프라이즈 박스`/`케이크`/`풍선`, 슬러그 힌트 `영문 소문자·숫자·하이픈 3~32자, 비워두면 자동으로 만들어져요`, 공개 예정 일시 힌트 `지금부터 30일 이내로 설정해 주세요`, 버튼 `미리보기`, 필수값 오류 `태명, 성별, 공개 예정 일시, 테마를 입력해 주세요`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/components/owner/CreatePageForm.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreatePageForm } from './CreatePageForm';

describe('CreatePageForm', () => {
  it('collects the required fields and calls onSubmit with a PageCreatePayload', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<CreatePageForm onSubmit={onSubmit} submitting={false} error={null} />);

    await user.type(screen.getByLabelText('태명'), '뽀튼이');
    await user.click(screen.getByRole('radio', { name: '남아' }));
    await user.type(screen.getByLabelText('공개 예정 일시'), '2026-10-01T09:00');
    await user.click(screen.getByRole('radio', { name: '케이크' }));
    await user.click(screen.getByRole('button', { name: '미리보기' }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    const payload = onSubmit.mock.calls[0][0];
    expect(payload.nickname).toBe('뽀튼이');
    expect(payload.actualGender).toBe('boy');
    expect(payload.theme).toBe('cake');
    expect(payload.bgmEnabled).toBe(false);
    expect(payload.dueDate).toBeNull();
    expect(payload.message).toBeNull();
    expect(payload.slug).toBeUndefined();
    expect(new Date(payload.revealAt).getTime()).toBe(new Date('2026-10-01T09:00').getTime());
  });

  it('includes optional fields when filled in', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<CreatePageForm onSubmit={onSubmit} submitting={false} error={null} />);

    await user.type(screen.getByLabelText('태명'), '뽀튼이');
    await user.click(screen.getByRole('radio', { name: '여아' }));
    await user.type(screen.getByLabelText('공개 예정 일시'), '2026-10-01T09:00');
    await user.type(screen.getByLabelText('출산 예정일 (선택)'), '2026-11-03');
    await user.type(screen.getByLabelText('축하 메시지 (선택)'), '건강하게 만나요');
    await user.click(screen.getByRole('radio', { name: '풍선' }));
    await user.type(screen.getByLabelText('커스텀 주소 (선택)'), 'our-baby');
    await user.click(screen.getByRole('button', { name: '미리보기' }));

    const payload = onSubmit.mock.calls[0][0];
    expect(payload.dueDate).toBe('2026-11-03');
    expect(payload.message).toBe('건강하게 만나요');
    expect(payload.theme).toBe('balloon');
    expect(payload.slug).toBe('our-baby');
  });

  it('refuses to submit without the required fields', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<CreatePageForm onSubmit={onSubmit} submitting={false} error={null} />);

    await user.click(screen.getByRole('button', { name: '미리보기' }));

    expect(screen.getByRole('alert')).toHaveTextContent('태명, 성별, 공개 예정 일시, 테마를 입력해 주세요');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('shows a server-provided error', () => {
    render(<CreatePageForm onSubmit={() => {}} submitting={false} error="이미 사용 중인 주소예요" />);

    expect(screen.getByRole('alert')).toHaveTextContent('이미 사용 중인 주소예요');
  });

  it('disables the submit button while submitting', () => {
    render(<CreatePageForm onSubmit={() => {}} submitting error={null} />);

    expect(screen.getByRole('button', { name: '미리보기' })).toBeDisabled();
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/components/owner/CreatePageForm.tsx`:
```tsx
'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '../Button';
import type { Gender, PageCreatePayload, Theme } from '@/lib/types';

const THEMES: { value: Theme; label: string }[] = [
  { value: 'box', label: '서프라이즈 박스' },
  { value: 'cake', label: '케이크' },
  { value: 'balloon', label: '풍선' },
];

const inputClass =
  'w-full rounded-radius-sm border border-border bg-surface-100 px-space-2 py-space-2 text-body outline-none focus:border-accent-primary';

export function CreatePageForm({
  onSubmit,
  submitting,
  error,
}: {
  onSubmit: (payload: PageCreatePayload) => void;
  submitting: boolean;
  error: string | null;
}) {
  const [nickname, setNickname] = useState('');
  const [actualGender, setActualGender] = useState<Gender | null>(null);
  const [revealAt, setRevealAt] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [message, setMessage] = useState('');
  const [theme, setTheme] = useState<Theme | null>(null);
  const [slug, setSlug] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!nickname.trim() || !actualGender || !revealAt || !theme) {
      setValidationError('태명, 성별, 공개 예정 일시, 테마를 입력해 주세요');
      return;
    }
    setValidationError(null);
    onSubmit({
      nickname: nickname.trim(),
      actualGender,
      revealAt: new Date(revealAt).toISOString(),
      dueDate: dueDate || null,
      message: message.trim() || null,
      theme,
      bgmEnabled: false,
      slug: slug.trim() || undefined,
    });
  };

  return (
    <form onSubmit={submit} className="flex w-full flex-col gap-space-3">
      <h1 className="font-display text-display-lg">페이지 만들기</h1>

      <label className="flex flex-col gap-space-1 text-label">
        태명
        <input className={inputClass} value={nickname} onChange={(e) => setNickname(e.target.value)} />
      </label>

      <fieldset className="flex flex-col gap-space-1">
        <legend className="text-label">실제 성별</legend>
        <div className="flex gap-space-3">
          <label className="flex items-center gap-space-1">
            <input type="radio" name="actualGender" checked={actualGender === 'boy'} onChange={() => setActualGender('boy')} />
            남아
          </label>
          <label className="flex items-center gap-space-1">
            <input type="radio" name="actualGender" checked={actualGender === 'girl'} onChange={() => setActualGender('girl')} />
            여아
          </label>
        </div>
      </fieldset>

      <label className="flex flex-col gap-space-1 text-label">
        공개 예정 일시
        <input type="datetime-local" className={inputClass} value={revealAt} onChange={(e) => setRevealAt(e.target.value)} />
        <span className="text-body-sm text-ink-muted">지금부터 30일 이내로 설정해 주세요</span>
      </label>

      <label className="flex flex-col gap-space-1 text-label">
        출산 예정일 (선택)
        <input type="date" className={inputClass} value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
      </label>

      <label className="flex flex-col gap-space-1 text-label">
        축하 메시지 (선택)
        <textarea className={inputClass} rows={3} value={message} onChange={(e) => setMessage(e.target.value)} />
      </label>

      <fieldset className="flex flex-col gap-space-1">
        <legend className="text-label">리빌 테마</legend>
        <div className="flex gap-space-2">
          {THEMES.map((t) => (
            <label key={t.value} className="flex items-center gap-space-1">
              <input type="radio" name="theme" checked={theme === t.value} onChange={() => setTheme(t.value)} />
              {t.label}
            </label>
          ))}
        </div>
      </fieldset>

      <label className="flex flex-col gap-space-1 text-label">
        커스텀 주소 (선택)
        <input className={inputClass} value={slug} onChange={(e) => setSlug(e.target.value)} />
        <span className="text-body-sm text-ink-muted">영문 소문자·숫자·하이픈 3~32자, 비워두면 자동으로 만들어져요</span>
      </label>

      {(validationError || error) && (
        <p role="alert" className="text-body-sm text-danger">
          {validationError ?? error}
        </p>
      )}

      <Button type="submit" disabled={submitting}>
        미리보기
      </Button>
    </form>
  );
}
```

`gender-reveal/web/src/app/create/page.tsx`(이 태스크는 세션 가드 + 폼만 연결하고, 실제 제출/미리보기 흐름은 Task 8에서 이 파일을 다시 고친다):
```tsx
'use client';

import { useEffect } from 'react';
import { CreatePageForm } from '@/components/owner/CreatePageForm';
import { useOwnerSession } from '@/hooks/useOwnerSession';

export default function Page() {
  const session = useOwnerSession();

  useEffect(() => {
    if (session.status === 'unauthenticated') {
      window.location.href = '/login';
    }
  }, [session.status]);

  if (session.status !== 'authenticated') {
    return null;
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[480px] flex-col px-space-3 py-space-5">
      <CreatePageForm onSubmit={() => {}} submitting={false} error={null} />
    </main>
  );
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/app/create web/src/components/owner/CreatePageForm.tsx web/src/components/owner/CreatePageForm.test.tsx
git commit -m "feat(web): 페이지 작성 폼(/create) 추가"
```

---

### Task 8: 미리보기 → 발행 → 성공 화면

**Files:**
- Create: `gender-reveal/web/src/components/owner/CreatePagePreview.tsx`
- Create: `gender-reveal/web/src/components/owner/PublishSuccessScreen.tsx`
- Create: `gender-reveal/web/src/components/owner/CreatePageFlow.tsx`
- Create: `gender-reveal/web/src/components/owner/CreatePageFlow.test.tsx`
- Modify: `gender-reveal/web/src/app/create/page.tsx`

**Interfaces:**
- Consumes: `CreatePageForm`(Task 7), `createPage`(Task 2), `zodiacFromDueDate`/`zodiacLabelKo`/`revealSrc`(Plan 4), `ApiError`(Plan 4)
- Produces: `<CreatePagePreview payload onEdit onPublish submitting />`, `<PublishSuccessScreen slug />`, `<CreatePageFlow />`(입력→미리보기→발행 상태 머신, 슬러그 409를 폼 오류로 되돌림)

**카피(새로 정함):** 미리보기 제목 `이렇게 만들어져요`, 되돌리기 `← 다시 입력하기`, 발행 버튼 `발행하기`, 슬러그 충돌 오류 `이미 사용 중인 주소예요`, 그 외 오류 `잠시 후 다시 시도해 주세요`, 성공 제목 `페이지가 발행됐어요!`, 안내 `이메일로도 링크를 보내드렸어요`, 복사 버튼 `링크 복사`, 복사 완료 `복사했어요`, 이동 버튼 `대시보드로 이동`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/components/owner/CreatePageFlow.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreatePageFlow } from './CreatePageFlow';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, createPage: vi.fn() };
});

const createPage = vi.mocked(api.createPage);

async function fillMinimalForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('태명'), '뽀튼이');
  await user.click(screen.getByRole('radio', { name: '남아' }));
  await user.type(screen.getByLabelText('공개 예정 일시'), '2026-10-01T09:00');
  await user.click(screen.getByRole('radio', { name: '서프라이즈 박스' }));
}

beforeEach(() => {
  createPage.mockReset();
  Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
});

describe('CreatePageFlow', () => {
  it('walks form → preview → publish → success', async () => {
    createPage.mockResolvedValue({ slug: 'ppo-2026' });
    const user = userEvent.setup();
    render(<CreatePageFlow />);

    await fillMinimalForm(user);
    await user.click(screen.getByRole('button', { name: '미리보기' }));

    expect(screen.getByText('이렇게 만들어져요')).toBeInTheDocument();
    expect(screen.getByText('뽀튼이')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '발행하기' }));

    expect(createPage).toHaveBeenCalledTimes(1);
    expect(await screen.findByText('페이지가 발행됐어요!')).toBeInTheDocument();
    expect(screen.getByText('이메일로도 링크를 보내드렸어요')).toBeInTheDocument();
    expect(screen.getByText(/ppo-2026/)).toBeInTheDocument();
  });

  it('lets the owner go back from preview to edit the form', async () => {
    const user = userEvent.setup();
    render(<CreatePageFlow />);

    await fillMinimalForm(user);
    await user.click(screen.getByRole('button', { name: '미리보기' }));
    await user.click(screen.getByRole('button', { name: '← 다시 입력하기' }));

    expect(screen.getByLabelText('태명')).toHaveValue('뽀튼이');
    expect(createPage).not.toHaveBeenCalled();
  });

  it('shows a slug-taken error on the form after a 409', async () => {
    createPage.mockRejectedValue(new api.ApiError(409, { error: 'Slug already taken' }));
    const user = userEvent.setup();
    render(<CreatePageFlow />);

    await fillMinimalForm(user);
    await user.click(screen.getByRole('button', { name: '미리보기' }));
    await user.click(screen.getByRole('button', { name: '발행하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 사용 중인 주소예요');
    expect(screen.getByLabelText('태명')).toBeInTheDocument();
  });

  it('copies the link on the success screen', async () => {
    createPage.mockResolvedValue({ slug: 'ppo-2026' });
    const user = userEvent.setup();
    render(<CreatePageFlow />);

    await fillMinimalForm(user);
    await user.click(screen.getByRole('button', { name: '미리보기' }));
    await user.click(screen.getByRole('button', { name: '발행하기' }));
    await user.click(await screen.findByRole('button', { name: '링크 복사' }));

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(expect.stringContaining('/g/ppo-2026'));
    expect(await screen.findByText('복사했어요')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/components/owner/CreatePagePreview.tsx`:
```tsx
import { Button } from '../Button';
import { babySrc } from '@/lib/illustrations';
import { REVEAL_PROMPT } from '@/lib/result';
import { zodiacFromDueDate, zodiacLabelKo } from '@/lib/zodiac';
import type { PageCreatePayload } from '@/lib/types';

export function CreatePagePreview({
  payload,
  onEdit,
  onPublish,
  submitting,
}: {
  payload: PageCreatePayload;
  onEdit: () => void;
  onPublish: () => void;
  submitting: boolean;
}) {
  const zodiac = zodiacFromDueDate(payload.dueDate);

  return (
    <div className="flex w-full flex-col gap-space-3">
      <button type="button" onClick={onEdit} className="self-start text-label text-ink-muted">
        ← 다시 입력하기
      </button>
      <h1 className="font-display text-display-lg">이렇게 만들어져요</h1>
      <div className="flex flex-col items-center gap-space-2 rounded-radius-md border border-border bg-surface-100 p-space-4">
        <img src={babySrc(payload.actualGender)} alt="" className="h-24 w-24" />
        <p className="text-body-lg">{payload.nickname}</p>
        {zodiac && <p className="text-body-sm text-ink-muted">{zodiacLabelKo(zodiac)}</p>}
        <p className="text-body">{REVEAL_PROMPT[payload.theme]}</p>
        {payload.message && <p className="text-body text-ink-muted">{payload.message}</p>}
      </div>
      <p className="text-body-sm text-ink-muted">발행 후 실제 화면에서 확인해 보세요</p>
      <Button onClick={onPublish} disabled={submitting}>
        발행하기
      </Button>
    </div>
  );
}
```

`gender-reveal/web/src/components/owner/PublishSuccessScreen.tsx`:
```tsx
'use client';

import { useState } from 'react';
import { Button } from '../Button';

export function PublishSuccessScreen({ slug }: { slug: string }) {
  const [copied, setCopied] = useState(false);
  const link = `${window.location.origin}/g/${slug}`;

  const onCopy = async () => {
    await navigator.clipboard.writeText(link);
    setCopied(true);
  };

  return (
    <div className="flex w-full flex-col items-center gap-space-3 text-center">
      <p className="font-display text-display-lg">페이지가 발행됐어요!</p>
      <p className="text-body text-ink-muted">이메일로도 링크를 보내드렸어요</p>
      <p className="break-all rounded-radius-md bg-surface-200 px-space-3 py-space-2 text-body">{link}</p>
      <Button onClick={onCopy}>{copied ? '복사했어요' : '링크 복사'}</Button>
      <a href="/dashboard" className="text-label text-ink-muted underline">
        대시보드로 이동
      </a>
    </div>
  );
}
```

`gender-reveal/web/src/components/owner/CreatePageFlow.tsx`:
```tsx
'use client';

import { useState } from 'react';
import { CreatePageForm } from './CreatePageForm';
import { CreatePagePreview } from './CreatePagePreview';
import { PublishSuccessScreen } from './PublishSuccessScreen';
import { ApiError, createPage } from '@/lib/api';
import type { PageCreatePayload } from '@/lib/types';

type Stage = { kind: 'form' } | { kind: 'preview'; payload: PageCreatePayload } | { kind: 'done'; slug: string };

export function CreatePageFlow() {
  const [stage, setStage] = useState<Stage>({ kind: 'form' });
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  if (stage.kind === 'done') {
    return <PublishSuccessScreen slug={stage.slug} />;
  }

  if (stage.kind === 'preview') {
    const publish = async () => {
      setSubmitting(true);
      try {
        const { slug } = await createPage(stage.payload);
        setStage({ kind: 'done', slug });
      } catch (error) {
        setFormError(
          error instanceof ApiError && error.status === 409 ? '이미 사용 중인 주소예요' : '잠시 후 다시 시도해 주세요',
        );
        setStage({ kind: 'form' });
      } finally {
        setSubmitting(false);
      }
    };

    return (
      <CreatePagePreview
        payload={stage.payload}
        onEdit={() => setStage({ kind: 'form' })}
        onPublish={publish}
        submitting={submitting}
      />
    );
  }

  return (
    <CreatePageForm
      onSubmit={(payload) => setStage({ kind: 'preview', payload })}
      submitting={submitting}
      error={formError}
    />
  );
}
```
주의: `CreatePageForm`은 필드 상태를 컴포넌트 내부에 갖고 있으므로, `preview → form`으로 돌아가면 새 `CreatePageForm` 인스턴스가 마운트돼 입력값이 비워진다. 이 계획의 범위에서는 허용한다(값 보존은 후속 다듬기로 남긴다) — 단, 위 `CreatePageFlow.test.tsx`의 "돌아가면 값이 남아있다" 테스트가 이미 통과해야 하므로, 실제로는 `stage.kind === 'form'`으로 돌아갈 때 이전에 입력했던 값을 잃지 않도록 `CreatePageForm`을 **언마운트하지 않아야 한다**. 이를 위해 `CreatePageFlow`는 `CreatePageForm`을 항상 렌더하되 `preview`/`done` 단계에서는 `hidden`으로 감춘다:
```tsx
  return (
    <>
      <div hidden={stage.kind !== 'form'}>
        <CreatePageForm
          onSubmit={(payload) => setStage({ kind: 'preview', payload })}
          submitting={submitting}
          error={formError}
        />
      </div>
      {stage.kind === 'preview' && (
        <CreatePagePreview
          payload={stage.payload}
          onEdit={() => setStage({ kind: 'form' })}
          onPublish={publish}
          submitting={submitting}
        />
      )}
    </>
  );
```
이 경우 `publish` 클로저를 `stage.kind === 'preview'`일 때만 정의할 수 없으므로, `publish(payload: PageCreatePayload)`를 최상위 함수로 두고 `onPublish={() => publish(stage.payload)}`처럼 호출하도록 구조를 정리한다. 구현자는 위 두 스니펫을 참고해 "미리보기에서 뒤로 가면 입력값이 남아있다"는 요구를 만족하는 가장 단순한 구조로 작성한다(테스트가 최종 판단 기준).

`gender-reveal/web/src/app/create/page.tsx`를 다음으로 교체:
```tsx
'use client';

import { useEffect } from 'react';
import { CreatePageFlow } from '@/components/owner/CreatePageFlow';
import { useOwnerSession } from '@/hooks/useOwnerSession';

export default function Page() {
  const session = useOwnerSession();

  useEffect(() => {
    if (session.status === 'unauthenticated') {
      window.location.href = '/login';
    }
  }, [session.status]);

  if (session.status !== 'authenticated') {
    return null;
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[480px] flex-col px-space-3 py-space-5">
      <CreatePageFlow />
    </main>
  );
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: 빌드 확인**

실행: `cd gender-reveal/web && npm run build`
예상: 성공. `out/login.html`, `out/dashboard.html`, `out/create.html` 존재.

- [ ] **Step 6: 커밋**

```bash
cd gender-reveal
git add web/src/components/owner/CreatePagePreview.tsx web/src/components/owner/PublishSuccessScreen.tsx web/src/components/owner/CreatePageFlow.tsx web/src/components/owner/CreatePageFlow.test.tsx web/src/app/create/page.tsx
git commit -m "feat(web): 미리보기·발행·성공 화면으로 페이지 작성 플로우 완성"
```

---

### Task 9: nginx 정리 + README 갱신

**Files:**
- Modify: `gender-reveal/web/nginx/gender-reveal.conf`
- Modify: `gender-reveal/web/README.md`

**Interfaces:**
- Consumes: 없음 (문서/설정 정리)
- Produces: 정확한 nginx 주석, 로그인 플로우 개발 안내

- [ ] **Step 1: nginx 주석 정리**

`gender-reveal/web/nginx/gender-reveal.conf`에서 다음 줄을 제거한다:
```nginx
    # TODO(Plan 5): /dashboard/<slug> needs the same shell treatment as /g/<slug>
    # once the dashboard page exists.
```
대신 파일 맨 아래(또는 해당 위치)에 짧게 남긴다:
```nginx
    # /login, /dashboard, /create are plain static files (no dynamic path segment —
    # the dashboard reads ?slug=... client-side), so the default `location /` block
    # below already serves them; no extra rule needed (see Plan 5).
```

- [ ] **Step 2: README 갱신**

`gender-reveal/web/README.md`의 "개발" 절 바로 아래에 다음을 추가한다:
```markdown
## 소유자 로그인 플로우 (로컬 개발)

1. API가 로그인 메일을 실제로 보내지 않는다(`RESEND_API_KEY` 미설정 시 로그만 남김) — 서버 로그에서
   `/api/auth/callback?token=...` 링크를 찾아 브라우저로 직접 열면 로그인된다.
2. 로그인에 성공하면 `owner_session` 쿠키가 발급되고 `/dashboard`로 이동한다.
3. `/dashboard`, `/create`는 세션이 없으면 자동으로 `/login`으로 돌아간다.
```

- [ ] **Step 3: 커밋**

```bash
cd gender-reveal
git add web/nginx/gender-reveal.conf web/README.md
git commit -m "docs(web): nginx 주석 정리, 소유자 로그인 개발 안내 추가"
```

---

## 이 계획 완료 후 상태

- 소유자는 `/login`에서 매직링크를 요청해 로그인하고, `/dashboard`에서 자기 페이지 목록·통계·방명록·보관주기를 관리하며, `/create`에서 새 페이지를 만들어 발행하고 링크를 복사할 수 있다.
- 백엔드는 소유자 자신에게 실제 성별 등 전체 설정을 보여주는 조회 엔드포인트 하나만 추가됐다(그 외 Plan 1–3 그대로).
- 여전히 없는 것: 공개 예정 일시 수정, 애니메이션 미리보기, BGM/카운트다운/QR(모두 후속 과제로 명시), Docker/nginx 문법 검증(Docker 설치 후).

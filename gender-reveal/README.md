# 젠더리빌 (`gender-reveal/`)

"젠더리빌" 이벤트 페이지 서비스. 백엔드는 [api/](api/)(Spring Boot), 프론트엔드는
[web/](web/)(Next.js)에 있다. 프로젝트 배경·기획 문서는 [planning/](planning/),
구현 계획/설계 문서는 [docs/superpowers/](docs/superpowers/), AI 에이전트용 작업
가이드는 [CLAUDE.md](CLAUDE.md)를 참고한다.

이 문서는 **기술 스펙**과 **로컬 개발 환경을 실제로 띄우는 방법**(인텔리제이 + VS Code 기준)을
다룬다. Docker/운영 배포는 아래 [Docker는 아직](#docker는-아직) 참고.

## 사전 준비

- **JDK 21** — `api/build.gradle.kts`가 Gradle 툴체인으로 21을 요구한다. 로컬에 없으면
  Gradle이 자동으로 다운로드하지만, 인텔리제이에서 편하게 쓰려면 미리 설치해 두는 게 좋다.
- **Node 20 이상**(또는 18.18+) — `web/`의 Next.js 15가 요구하는 최소 버전.
- 둘 다 저장소에 각각 wrapper/lockfile이 있어 별도 버전 관리자 설치는 필요 없다
  (`api/gradlew`, `web/package-lock.json`).

## 기술 스펙

### 백엔드 (`api/`)

- **언어/런타임**: Java 21 (Gradle 툴체인이 강제)
- **프레임워크**: Spring Boot 3.3.4 — `spring-boot-starter-web`, `-data-jpa`, `-validation`
- **빌드**: Gradle (Kotlin DSL), wrapper 포함
- **DB**: SQLite (`org.xerial:sqlite-jdbc`) + `hibernate-community-dialects`,
  마이그레이션은 Flyway 전용(`ddl-auto: none`, `src/main/resources/db/migration/V*.sql`)
- **이메일**: 개발은 로그 폴백(`LoggingEmailSender`), 운영은 Resend HTTP API를 별도 SDK 없이
  `java.net.http.HttpClient`로 직접 호출(`ResendEmailSender`)
- **테스트**: JUnit 5 + AssertJ + Spring Boot Test(`MockMvc`)
- **핵심 컨벤션**:
  - 시간은 항상 주입된 `Clock`(`Instant.now(clock)`) — 테스트에서 시간 이동 가능
  - `Instant`/`LocalDate` 컬럼은 전용 문자열 컨버터(`InstantStringConverter`,
    `LocalDateStringConverter`)로 ISO 텍스트 저장 — SQLite JDBC 드라이버의 기본
    epoch-millis 바인딩을 피하기 위함
  - 도메인 예외 + 패키지별 `@RestControllerAdvice`(제너릭 catch-all 없음)
  - 게스트는 익명 쿠키(`guest_id`), 소유자는 이메일 매직링크 로그인 + 세션 쿠키(`owner_session`)
  - 게스트 쿠키 기준 in-memory 슬라이딩 윈도우 레이트리밋(맞추기·방명록 각 분당 5회)
  - 매일 새벽 4시 만료 매직링크 토큰·소유자 세션 정리 스케줄러(`@Scheduled`)
- **API 개요**: `POST/GET /api/pages`, `POST /api/pages/{slug}/guesses`,
  `GET/POST /api/pages/{slug}/guestbook`, `POST /api/auth/magic-link` ·
  `GET /api/auth/callback` · `GET /api/auth/me` · `POST /api/auth/logout`,
  `GET/POST /api/owner/pages/**`

### 프론트엔드 (`web/`)

- **언어**: TypeScript, React 19
- **프레임워크**: Next.js 15 (App Router), **정적 export**(`output: 'export'`) — Node 서버 없이
  빌드 산출물(`out/`)을 nginx가 서빙
- **스타일**: Tailwind CSS 3 — 색상·타이포·radius 토큰은
  `planning/design-system/project/tokens.json`을 소스로 `tailwind.config.ts`에 반영
  (테스트로 두 파일이 항상 일치하는지 교차검증)
- **테스트**: Vitest + React Testing Library + jsdom
- **이미지**: OG 공유 카드 이미지는 `sharp`로 빌드 타임에 생성(`npm run og`)
- **라우트**: `/g/<slug>`(방문자, 슬러그를 `window.location.pathname`에서 클라이언트가 읽음),
  `/login`, `/dashboard`(`?slug=` 쿼리로 목록/상세 분기), `/create` — 전부 클라이언트 컴포넌트이며
  정적 export 제약상 서버 사이드 동적 라우팅은 쓰지 않음
- **개발 중 API 프록시**: `next.config.ts`의 rewrite로 `/api/*` → `API_ORIGIN`(기본
  `http://localhost:8080`)

## 1) 백엔드 — 인텔리제이로 `api/` 실행

1. 인텔리제이에서 `gender-reveal/api`를 열거나(또는 저장소 루트를 열면 Gradle 프로젝트로
   자동 인식됨), Gradle 동기화가 끝나길 기다린다.
2. **Project Structure → SDKs**에서 JDK 21이 선택돼 있는지 확인한다.
3. **최초 실행 전 1회만**: `api/data` 폴더를 미리 만들어 둔다.
   ```bash
   mkdir -p gender-reveal/api/data
   ```
   SQLite 드라이버가 파일은 만들어도 상위 폴더까지는 안 만들어서, 폴더가 없으면
   `path to './data': '.../data' does not exist` 에러로 부팅이 실패한다.
4. 실행 방법은 두 가지가 있다 — 상황에 맞게 고른다.

   **A. Gradle `bootRun` (가장 간단, working directory 문제 없음)**

   Gradle 도구 창 → `api` → `Tasks` → `application` → `bootRun` 더블클릭
   (또는 인텔리제이 내장 터미널에서 `cd api && ./gradlew bootRun`). Gradle이 실행하는
   태스크는 작업 디렉터리를 항상 `api/` 기준으로 올바르게 잡는다.

   **B. "Application" 실행 구성 (브레이크포인트 디버깅 등에 편리)**

   `com.genderreveal.api.GenderRevealApiApplication`을 메인 클래스로 지정해 만든 일반
   Application 실행 구성으로도 당연히 실행할 수 있다. 다만 이 구성의 **Working directory는
   신뢰하지 않는다** — 저장소를 `event-echo/` 통째로 열어둔 상태(worktree 등 다른 하위
   폴더가 함께 있는 구조)에서는 인텔리제이가 이 값을 잘못 잡거나(프로젝트 최상위 폴더,
   심지어 존재하지 않는 옛 worktree 경로 등), Run/Debug Configurations에서 고쳐도 반영이
   안 되는 사례가 실제로 있었다. Working directory에 의존하는 대신, **환경변수로 DB 경로를
   절대경로로 못박아서** 이 문제를 원천적으로 피한다:

   1. `Run → Edit Configurations...` → 해당 구성 선택(또는 `+` → `Application`으로 새로
      만들고 Main class를 `com.genderreveal.api.GenderRevealApiApplication`으로 지정)
   2. **Environment variables** 필드가 안 보이면 상단의 **"Modify options"**에서
      `Environment variables`를 체크해 필드를 노출시킨다
   3. 필드 옆 아이콘 클릭 → `+` → `GENDER_REVEAL_DB_PATH` =
      `/Users/hanwha/Workspace/event-echo/gender-reveal/api/data/gender-reveal.db` 추가 → OK
   4. Apply → OK로 저장. 이제 Working directory가 뭐로 잡히든 이 경로를 그대로 쓴다
      (`application.yml`의 `${GENDER_REVEAL_DB_PATH:./data/gender-reveal.db}`가 환경변수가
      있으면 그 값을 우선하기 때문).
   5. 기존에 이 방식의 실행 구성을 이미 만들어 뒀는데 여전히 안 된다면, 수정 대신
      **삭제 후 새로 생성**한다 — 캐시된 잘못된 값이 수정으로는 안 지워지는 경우가 있었다.

5. `http://localhost:8080`에서 뜬다. 최초 기동 시 Flyway가 SQLite 스키마를 자동
   마이그레이션한다.

개발용 환경변수(전부 기본값으로 동작하며, 필요할 때만 바꾸면 됨):

| 변수 | 기본값 | 설명 |
|---|---|---|
| `RESEND_API_KEY` | (없음) | 비워두면 매직링크 로그인 메일이 실제 발송되지 않고 애플리케이션 로그에만 링크가 출력된다(개발용 폴백). |
| `APP_BASE_URL` | `http://localhost:8080` | 매직링크·리다이렉트에 쓰이는 백엔드 자신의 base URL. |
| `GENDER_REVEAL_DB_PATH` | `./data/gender-reveal.db` | SQLite 파일 경로(`api/` 기준 상대경로). |

## 2) 프론트엔드 — VS Code로 `web/` 실행

1. VS Code에서 `gender-reveal/web`을 열거나(또는 저장소 루트를 열어도 무방) 통합 터미널을 연다.
2. ```bash
   cd web
   npm install
   npm run dev
   ```
3. `http://localhost:3000`에서 뜬다. `next.config.ts`의 rewrite 설정으로 `/api/*` 요청은
   개발 중 자동으로 `http://localhost:8080`(백엔드)으로 전달된다 — 백엔드가 먼저(또는 같이)
   떠 있어야 로그인/페이지 생성 등 API 호출이 되는 기능이 정상 동작한다.
4. 백엔드를 다른 호스트/포트로 띄웠다면 `API_ORIGIN=http://host:port npm run dev`로 지정.

## 3) 두 서버를 다 띄운 뒤 — 로그인해서 확인해보기

1. `http://localhost:3000/login`에서 이메일 입력 후 "로그인 링크 받기".
2. `RESEND_API_KEY`를 안 넣었다면 실제 메일이 안 온다 — **백엔드 콘솔/로그**에서
   `http://localhost:8080/api/auth/callback?token=...` 형태의 줄을 찾아 브라우저로 그 링크를
   **한 번만** 연다(토큰은 15분·1회용).
3. 로그인되면 `/dashboard`로 이동한다. "새 페이지 만들기"로 페이지 생성 플로우까지 확인 가능.

## 테스트

```bash
cd api && ./gradlew test    # 백엔드 전체 테스트
cd web && npm test          # 프론트 전체 테스트(Vitest)
```

## 자세한 프론트 개발 노트

빌드(`npm run build`)가 프로덕션 전용으로 `NEXT_PUBLIC_SITE_URL`을 요구하는 이유, OG 이미지
재생성, nginx 배포 설정 등은 [web/README.md](web/README.md)에 더 자세히 있다.

## Docker는 아직

nginx + api + web을 docker-compose로 한 번에 띄우는 것은 아직 준비돼 있지 않다(Plan 1의
Task 8이 nginx/web 추가 전 버전인 채로 남아 있음 — 개발 머신에 Docker가 없어서 검증도 안 됨).
지금은 위 방식대로 두 서버를 각각 띄워서 확인한다.

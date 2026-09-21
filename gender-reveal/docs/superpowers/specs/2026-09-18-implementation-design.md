# 젠더리빌 — 구현 설계

- 작성일: 2026-09-18
- 관련 문서: [Requirements.md](../../../planning/Requirements.md), [design-system](../../../planning/design-system/README.md), [screens](../../../planning/screens/README.md)
- 상태: 사용자 승인 완료 (브레인스토밍 대화를 통해 확정), 구현 계획(writing-plans) 착수 전 단계

## 1. 개요

"젠더리빌"은 기획 단계(요구사항/와이어프레임/디자인 시스템/일러스트/고충실도 화면)가 완료된
상태에서, 실제 소스코드 구현에 들어가기 위한 기술 스택과 아키텍처를 정의한다. 기능 요구사항의
출처는 [planning/Requirements.md](../../../planning/Requirements.md)이며, 이 문서는 "어떻게
만들 것인가"만 다룬다.

## 2. 기술 스택 결정과 근거

사용자와의 대화를 통해 하나씩 확정한 스택이며, 각 항목의 대안과 선택 이유를 남긴다.

| 영역 | 선택 | 대안 | 선택 이유 |
|---|---|---|---|
| 프론트엔드 프레임워크 | Next.js (TypeScript) | — | 사용자 지정 |
| 프론트 스타일링 | Tailwind CSS | CSS Modules + CSS 변수 | 디자인 시스템 토큰이 이미 명확히 정의돼 있어 `tailwind.config`에 그대로 매핑하기 좋음 |
| 백엔드 프레임워크 | Spring Boot (Java 21, Gradle) | Next.js API Routes로 풀스택 처리 | 사용자가 Spring 사용을 원함. 프론트/백엔드를 분리하되 모노레포로 관리 |
| 백엔드 데이터 접근 | Spring Data JPA (Hibernate) | MyBatis | 스키마가 단순(페이지/맞추기/방명록)해서 JPA 제약이 크게 부딪히지 않음. Hibernate 6+ 커뮤니티 SQLite dialect 사용 |
| DB 마이그레이션 | Flyway | Liquibase | SQL 파일 기반이라 단순한 스키마에 적합, Spring Boot 기본 통합 지원 |
| DB 엔진 | SQLite (임베디드 파일) | MongoDB(Docker 자체 호스팅), LokiJS(임베디드 파일) | 데이터 모델이 본질적으로 관계형(페이지→맞추기/방명록)이라 집계 쿼리(방문자 통계, 예측 비율)가 SQL로 훨씬 단순함. 별도 DB 프로세스 없이 파일 하나로 동작해 "내부에서 간단히 관리"라는 원 요구도 충족 |
| 배포/호스팅 | 직접 운영하는 서버/컨테이너 (Docker) | Vercel 서버리스 | 서버리스는 요청마다 함수가 새로 실행돼 로컬 파일 기반 DB(SQLite)를 못 씀. 직접 운영이면 이 제약이 없음 |
| 프론트-백엔드 연결 | nginx 리버스 프록시로 동일 오리진 통합 (`/api/**` → Spring, 나머지 → Next.js) | 완전 분리된 오리진 + CORS/쿠키 도메인 공유 | 같은 오리진이면 CORS 설정이 필요 없고 로그인 쿠키도 표준 same-site 쿠키로 단순하게 동작 |
| 인증 | 매직링크 이메일 로그인 + DB 세션 테이블(httpOnly 쿠키) | JWT(무상태) | 소유자가 가끔 로그인하는 용도라, 즉시 로그아웃/만료가 row 삭제로 끝나는 DB 세션이 JWT의 블랙리스트 관리보다 단순 |
| 이메일 발송 | Resend (트랜잭션 이메일 API) | 기존 SMTP 계정 직접 연동 | 전달률/스팸 처리가 검증돼 있고 연동이 간단. 사용 중인 메일 계정이 따로 없어 추천안 채택 |
| 저장소 구조 | 모노레포 (`web/`, `api/`, `docker-compose.yml`을 한 저장소에) | 저장소 분리 | 버전 관리를 하나로 가져가고 싶다는 요구. 런타임은 여전히 두 개의 프로세스(Next.js/Spring)로 분리됨 |

## 3. 저장소 구조

```
gender-reveal/
├── web/                              # Next.js (TypeScript) — 프론트엔드
│   ├── app/
│   │   ├── g/[slug]/                 # 방문자 플로우 (상태에 따라 인트로/선택/결과/방명록/비밀/만료 분기)
│   │   ├── create/                   # 페이지 작성 폼
│   │   └── dashboard/[slug]/         # 관리자 대시보드
│   └── tailwind.config.ts            # 디자인 시스템 토큰 매핑
├── api/                               # Spring Boot (Java 21, Gradle) — 백엔드
│   ├── src/main/java/.../
│   │   ├── page/                     # 페이지 생성/조회/상태 계산
│   │   ├── guess/                    # 맞추기
│   │   ├── guestbook/                # 방명록
│   │   └── auth/                     # 매직링크, 세션
│   └── src/main/resources/db/migration/   # Flyway SQL
├── nginx/
│   └── default.conf                  # 리버스 프록시 라우팅
├── docker-compose.yml
├── planning/                          # 기존 기획 문서 (변경 없음)
└── docs/superpowers/specs/            # 이 문서
```

- 데이터베이스 파일(SQLite)은 Docker named volume에 저장해 컨테이너 재시작에도 유지한다.

## 4. 데이터 모델 (SQLite)

| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| `pages` | id, slug(unique), nickname, actual_gender, reveal_at, due_date(nullable), message, theme(box/cake/balloon), bgm_enabled, owner_email, created_at, expires_at, extended(boolean) | `expires_at`은 `created_at + 30일`로 생성 시 계산해 저장(연장 시 갱신) |
| `guesses` | id, page_id(FK), guest_cookie_id, guessed_gender, created_at | `unique(page_id, guest_cookie_id)`로 1인 1회 제한 |
| `guestbook_entries` | id, page_id(FK), nickname, message, hidden(boolean), created_at | |
| `magic_link_tokens` | id, page_id 또는 owner_email, token_hash, expires_at, used(boolean) | 토큰은 평문 저장하지 않고 해시로 저장 |
| `owner_sessions` | id, page_id(FK), session_token_hash, expires_at, created_at | 세션 쿠키 값은 이 테이블의 토큰과 매칭 |

**페이지 상태(`secret`/`open`/`expired`)는 컬럼으로 저장하지 않고 조회 시점에 계산한다** — `now < reveal_at`이면 `secret`, `now >= expires_at`이면 `expired`(단 `extended`면 만료일 재계산), 그 외는 `open`. 별도 상태 동기화 배치가 필요 없다.

## 5. API 설계 (REST, `/api` 하위)

**공개 (게스트, 인증 불필요)**
- `GET /api/pages/{slug}` — 계산된 상태 + 상태별로 노출 가능한 필드만 반환 (예: secret 상태에선 actual_gender 절대 미노출)
- `POST /api/pages/{slug}/guesses` — 쿠키 기반 1회 제한, 중복 시 409
- `GET /api/pages/{slug}/guestbook` — 숨김 처리된 항목 제외
- `POST /api/pages/{slug}/guestbook`

**소유자 (세션 쿠키 필요)**
- `POST /api/auth/magic-link` — 이메일 입력 → 토큰 발급 + Resend 발송
- `GET /api/auth/callback?token=...` — 토큰 검증 → 세션 생성 → httpOnly 쿠키 설정 → 대시보드 리다이렉트
- `POST /api/pages` — 페이지 생성(작성 플로우)
- `GET /api/owner/pages/{slug}/stats` — 방문자 수/맞추기 참여자 수/예측 비율
- `GET·PATCH /api/owner/pages/{slug}/guestbook` — 목록 조회, 노출/숨김/삭제
- `POST /api/owner/pages/{slug}/extend` — 1회 한정 30일 연장 (이미 `extended`면 409)

## 6. 인증 플로우 (매직링크)

1. 소유자가 대시보드 로그인 화면에서 이메일 입력 → `POST /api/auth/magic-link`
2. 서버가 임의 토큰 생성 → `magic_link_tokens`에 해시로 저장(만료 15분) → Resend로 링크 이메일 발송
3. 이메일의 링크 클릭 → `GET /api/auth/callback?token=...`
4. 토큰 검증(만료/사용 여부, 사용 후 즉시 `used=true`) → `owner_sessions`에 세션 생성(만료 7일 등) → httpOnly + Secure + SameSite=Lax 쿠키 설정 → 대시보드로 리다이렉트

## 7. 프론트엔드 구조

- `/g/[slug]` — 서버에서 상태를 조회해 인트로/선택/결과/방명록 화면을 분기 렌더 (고충실도 목업 [planning/screens/](../../../planning/screens/README.md)의 Main/Select/Result_*/Guestbook에 대응)
- `/g/[slug]` 상태가 `secret`/`expired`면 각각 Secret/Expired 화면을 렌더
- `/create` — 소유자가 페이지를 작성하는 폼 (닉네임, 실제 성별, 공개 일시, 출산예정일, 문구, 테마, BGM 사용 여부, 미리보기)
- `/dashboard/[slug]` — 관리자 대시보드 (세션 쿠키 없으면 매직링크 로그인 화면으로)
- `tailwind.config.ts`에 [design-system/project/tokens.json](../../../planning/design-system/project/tokens.json)의 컬러/타이포/스페이싱/라운드 토큰을 이식
- 일러스트 SVG 24종은 `web/public/illustrations/`(또는 유사 경로)로 복사해 정적 자산으로 서빙

## 8. 테스트 전략

- **Spring**: JUnit5 + Spring Boot Test. 컨트롤러/서비스 단위 테스트는 인메모리 SQLite(`jdbc:sqlite::memory:` 또는 임시 파일)로 실행 — Testcontainers는 SQLite엔 불필요.
- **Next.js**: Vitest + React Testing Library로 컴포넌트/유틸 테스트. 핵심 플로우(인트로→선택→결과→방명록, 매직링크 로그인)는 Playwright E2E를 선택적으로 도입.

## 9. 이번 설계에서 보류한 것 (구현 계획 또는 이후 세션에서 결정)

- **BGM 음원 파일**: 아직 소싱되지 않음. 라이선스 확인 후 파일 선정 필요.
- **OG 이미지 자동 생성**: Next.js `ImageResponse` 등 구체 방식은 구현 계획 단계에서 결정.
- **QR 코드 생성**: Requirements.md에 "선택" 기능으로 표시돼 있어 1차 구현 범위 포함 여부를 다시 확인해야 함.
- **카운트다운 표시(Secret 화면)**: Requirements.md에 "추가 검토" 상태로 남아 있음 — 1차 범위 포함 여부 미정.

## 10. 다음 단계

이 설계를 바탕으로 `writing-plans` 스킬을 통해 구체적인 구현 계획(작업 단위, 순서, 각 단계의
완료 기준)을 작성한다.

## 11. Plan 3 추가 결정 (2026-09-21)

5·6절을 다음과 같이 바꾼다. 상세는 [Plan 3](../plans/2026-09-21-auth-and-owner-api.md).

- **로그인 먼저, 그다음 페이지 생성.** `POST /api/pages`는 소유자 세션이 필수이고 `ownerEmail`은 세션에서 가져온다(요청 본문 값은 무시).
- **세션·매직링크 토큰은 이메일에 붙는다.** 로그인 시점에는 페이지가 없으므로 `owner_sessions`/`magic_link_tokens`에서 `page_id`를 제거하고 `owner_email`로 대체(V2 마이그레이션). 대시보드 권한은 "세션 이메일 == 페이지 `ownerEmail`"로 판정하며, 남의 페이지는 404로 응답한다.
- **방문자 수 = 익명 쿠키 기준 고유 방문자.** `page_visits(page_id, guest_cookie_id)` UNIQUE. 방문자는 로그인하지 않는다.
- **소유자 대시보드 방명록에 작성자의 맞추기 결과 표시.** `guestbook_entries.guest_cookie_id`(nullable)를 `guesses`와 조인해 소유자 조회에서만 노출한다. 공개 방명록 응답은 그대로다.

## 12. Plan 4 추가 결정 (2026-09-21)

7절(프론트엔드 구조)을 다음과 같이 바꾼다. 상세는 [Plan 4](../plans/2026-09-21-web-visitor-screens.md).

- **Next.js는 정적 export**(`output: 'export'`)로 nginx가 서빙한다(Node 서버 없음). `/g/[slug]`는 서버 분기 렌더가 아니라 정적 셸(`g.html`) 하나를 nginx가 모든 `/g/*`에 내주고, 브라우저가 URL에서 슬러그를 읽어 동일 출처 `/api`를 호출해 그린다.
- **공유 OG 카드는 모든 링크가 동일한 이미지·제목.** 슬러그별 OG(닉네임 등)는 정적 export로 불가 — 필요해지면 Node SSR로 전환.
- **1차 범위 제외:** BGM, 비밀 화면 카운트다운, QR 코드(9절의 보류 항목 중 이 세 가지를 "제외"로 확정).
- **화면 배분:** Plan 4 = 방문자 화면(`/g/[slug]`), Plan 5 = `/login`, `/dashboard`, `/dashboard/[slug]`, `/create`.

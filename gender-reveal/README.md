# 젠더리빌 (`gender-reveal/`)

"젠더리빌" 이벤트 페이지 서비스. 백엔드는 [api/](api/)(Spring Boot), 프론트엔드는
[web/](web/)(Next.js)에 있다. 프로젝트 배경·기획 문서는 [planning/](planning/),
구현 계획/설계 문서는 [docs/superpowers/](docs/superpowers/), AI 에이전트용 작업
가이드는 [CLAUDE.md](CLAUDE.md)를 참고한다.

이 문서는 **로컬 개발 환경을 실제로 띄우는 방법**만 다룬다(인텔리제이 + VS Code 기준).
Docker/운영 배포는 아래 [Docker는 아직](#docker는-아직) 참고.

## 사전 준비

- **JDK 21** — `api/build.gradle.kts`가 Gradle 툴체인으로 21을 요구한다. 로컬에 없으면
  Gradle이 자동으로 다운로드하지만, 인텔리제이에서 편하게 쓰려면 미리 설치해 두는 게 좋다.
- **Node 20 이상**(또는 18.18+) — `web/`의 Next.js 15가 요구하는 최소 버전.
- 둘 다 저장소에 각각 wrapper/lockfile이 있어 별도 버전 관리자 설치는 필요 없다
  (`api/gradlew`, `web/package-lock.json`).

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
4. 실행 방법 중 하나:
   - Gradle 도구 창 → `api` → `Tasks` → `application` → `bootRun` 더블클릭
   - 또는 `com.genderreveal.api.GenderRevealApiApplication`을 일반 Application 실행
     구성으로 만들어 실행(우클릭 → Run)
   - 또는 인텔리제이 내장 터미널에서 `cd api && ./gradlew bootRun`
   - **Application 실행 구성을 쓴다면 Working directory를 꼭 확인**한다. 저장소 루트
     (`event-echo/`)를 통째로 열었을 때 인텔리제이가 이 값을 프로젝트 최상위 폴더로 잡는
     경우가 있는데, `GENDER_REVEAL_DB_PATH` 기본값(`./data/gender-reveal.db`)이 그 기준
     폴더에서 풀리면서 `path to './data/gender-reveal.db': '.../data' does not exist` 에러가
     난다 — Run/Debug Configurations에서 Working directory를 `gender-reveal/api`로 명시
     지정(또는 `$MODULE_WORKING_DIR$`)하고, 3번의 `data/` 폴더도 그 경로 기준으로 만든다.
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

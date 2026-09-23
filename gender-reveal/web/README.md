# 젠더리빌 웹 (`web/`)

Next.js 정적 export(Node 서버 없음). 방문자 화면은 `/g/<slug>` 한 셸이 브라우저에서 API를 호출해 그린다.

인텔리제이/VS Code로 백엔드까지 같이 띄우는 전체 개발 환경 구동 방법은
[../README.md](../README.md) 참고. 아래는 `web/`만 따로 다룰 때의 세부 내용이다.

## 개발

터미널 두 개:

```bash
# 1) API (gender-reveal/api)
./gradlew bootRun

# 2) 웹 (gender-reveal/web) — /api/* 는 localhost:8080 으로, /g/<slug> 는 /g 셸로 rewrite 된다
npm install
npm run dev
```

http://localhost:3000/g/<slug> 를 연다. 페이지 생성은 소유자 로그인이 필요하다 — `/login`으로 로그인한 뒤
`/create`에서 페이지를 만든다.
API 주소를 바꾸려면 `API_ORIGIN=http://host:port npm run dev`.

## 소유자 로그인 플로우 (로컬 개발)

1. API가 로그인 메일을 실제로 보내지 않는다(`RESEND_API_KEY` 미설정 시 로그만 남김) — 서버 로그에서
   `/api/auth/callback?token=...` 링크를 찾아 브라우저로 직접 열면 로그인된다.
2. 로그인에 성공하면 `owner_session` 쿠키가 발급되고 `/dashboard`로 이동한다.
3. `/dashboard`, `/create`는 세션이 없으면 자동으로 `/login`으로 돌아간다.

## 테스트 / 빌드

```bash
npm test          # Vitest
NEXT_PUBLIC_SITE_URL=https://example.com npm run build   # 정적 파일 → out/ (OG 이미지 절대 URL에 필요)
npm run og        # public/og.png 재생성 (일러스트 변경 시)
```

`npm run build`는 프로덕션 빌드(`NODE_ENV=production`)라 `NEXT_PUBLIC_SITE_URL`이 없으면 실패한다
(og:image/twitter:image가 `localhost`로 새는 것을 막기 위한 의도적인 가드 — `src/app/layout.tsx` 참고).
로컬에서 값을 정하지 않았다면 아무 값이나 넣어 빌드 결과만 확인해도 된다.

## 배포

`out/`을 nginx 루트(`/usr/share/nginx/html`)에 두고 `nginx/gender-reveal.conf`를 사용한다.
공유 카드는 모든 링크가 동일한 제목·이미지다(정적 export 제약, 성별/공개 여부 비노출).

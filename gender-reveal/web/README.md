# 젠더리빌 웹 (`web/`)

Next.js 정적 export(Node 서버 없음). 방문자 화면은 `/g/<slug>` 한 셸이 브라우저에서 API를 호출해 그린다.

## 개발

터미널 두 개:

```bash
# 1) API (gender-reveal/api)
./gradlew bootRun

# 2) 웹 (gender-reveal/web) — /api/* 는 localhost:8080 으로, /g/<slug> 는 /g 셸로 rewrite 된다
npm install
npm run dev
```

http://localhost:3000/g/<slug> 를 연다. 페이지 생성은 소유자 로그인이 필요하다(Plan 5 전까지는 API로 직접 만든다).
API 주소를 바꾸려면 `API_ORIGIN=http://host:port npm run dev`.

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

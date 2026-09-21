# 젠더리빌 프론트엔드 + 방문자 화면 구현 계획 (Plan 4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `gender-reveal/web/`에 Next.js 정적 export 프로젝트를 만들고, 방문자가 보는 화면 전부(인트로 → 맞추기 선택 → 결과 리빌(3개 테마) → 축하글, 그리고 비밀/만료 화면)를 구현한다. nginx 설정과 공통 OG 이미지도 포함한다.

**Architecture:** `next build`의 `output: 'export'`로 정적 파일(`web/out/`)만 만들고 nginx가 서빙한다(Node 서버 없음). 방문자 페이지는 `/g/<slug>` 한 URL 패턴이라 정적 셸 `g.html` 하나를 nginx가 모든 `/g/*` 요청에 내주고, 브라우저에서 URL의 슬러그를 읽어 동일 출처 `/api/...`를 호출해 화면을 그린다. 개발 중에는 `next dev`의 `rewrites`가 nginx 역할(`/g/:slug` → `/g`, `/api/*` → Spring `:8080`)을 대신한다. 화면 상태 전환(intro → select → result → guestbook)은 `GuestPage` 한 컴포넌트의 상태 머신이 맡고, 각 화면은 props만 받는 순수 컴포넌트다.

**Tech Stack:** Next.js 15(App Router, `output: 'export'`) · React 19 · TypeScript · Tailwind CSS 3.4 · Vitest + React Testing Library + jsdom · sharp(OG 이미지 생성용 devDependency). 백엔드(`api/`)는 수정하지 않는다.

**Spec:** [docs/superpowers/specs/2026-09-18-implementation-design.md](../specs/2026-09-18-implementation-design.md) — 7절(프론트엔드 구조)이 아래 "스펙에서 바뀐 결정"으로 갱신된다(스펙 12절에도 기록). 화면 카피·레이아웃은 [planning/screens/](../../../planning/screens/README.md)의 고충실도 목업을 따른다.

## 스펙에서 바뀐 결정 (사용자와 합의)

- **정적 export.** 스펙 7절의 "서버에서 상태를 조회해 분기 렌더"는 폐기한다. `/g/[slug]`는 브라우저에서 API를 호출해 그린다.
- **OG 카드는 공통 이미지·공통 제목.** 슬러그별 OG 태그(닉네임 등)는 정적 export로 만들 수 없다. 성별/공개 여부가 링크 미리보기로 새지 않는다는 장점도 있다. 슬러그별 카드가 필요해지면 Node SSR로 전환한다(이 계획의 범위 밖).
- **1차 범위에서 제외:** BGM(음원·재생 UI·선호 저장 전부), 비밀 화면 카운트다운, QR 코드.
- **`/login`, `/dashboard`, `/dashboard/[slug]`, `/create`는 Plan 5.** Plan 3의 매직링크 콜백이 `/dashboard`와 `/login?error=invalid`로 리다이렉트하므로 Plan 5가 끝나기 전까지 이 경로는 프론트에 없다(nginx 404). 만료 화면의 "소유자 로그인" 링크는 `/login`을 가리키지만 Plan 5 전까지는 404다.

## 이 계획에서 스펙을 넘어 내린 설계 판단

- **슬러그는 `window.location.pathname`에서 읽는다.** `usePathname()`은 정적 셸(`/g`)을 돌려줄 수 있어 쓰지 않는다. 마운트 후(`useEffect`)에 읽는다.
- **"이미 맞췄는지"를 API에 묻지 않고 맞추기 POST의 결과로 판단한다.** 재방문 시에도 인트로 → 선택 화면을 거치지만, 선택하면 백엔드가 409 + 기존 `guessedGender`를 돌려주므로 `submitGuess`가 그 값을 "이미 맞춘 예측"으로 그대로 결과 화면에 쓴다(`alreadyGuessed: true`). 409인데 `guessedGender`가 없으면(=페이지가 open이 아님) 예외를 던져 페이지를 다시 불러온다.
- **알려진 한계: `open` 응답에 `actualGender`가 들어 있다.** 맞추기 전에도 개발자 도구로 정답을 볼 수 있다. 백엔드가 이미 그렇게 설계돼 있고(Plan 1) 소규모 이벤트라 1차에서는 수용한다. 막으려면 "맞춘 뒤에만 정답을 주는" 별도 엔드포인트가 필요하며 별도 계획으로 다룬다.
- **띠는 양력 연도로 계산한다**(음력 설 경계 무시). 출산예정일이 1~2월인 경우 실제 띠와 하루 이틀 다를 수 있다.
- **리빌 연출 1차 범위:** 각 테마의 "리빌 전" 중립 그림(닫힌 선물상자/통케이크/풍선) → 탭 → 흔들림(0.9초) → 성별 테마 SVG가 커지며 등장 + 떠오르는 하트. 테마별 세부 연출(뚜껑이 열리며 풍선이 올라옴, 케이크 커팅, 풍선 팝 파편 등)은 후속 고도화로 미룬다. `prefers-reduced-motion`에서는 애니메이션 없이 즉시 표시한다.
- **`next/image`를 쓰지 않는다**(정적 export에서는 최적화 서버가 없다). 일러스트는 `public/illustrations/`의 SVG를 일반 `<img>`로 쓴다.
- **폰트는 `next/font/google`(`Hi Melody`, `Noto Sans KR`)을 `preload: false`로 로드한다.** 빌드 시 네트워크가 필요하다.
- **nginx 설정은 이 환경에서 문법 검증(`nginx -t`)을 하지 못한다**(nginx/Docker 없음). 파일만 작성하고, 검증은 Docker 태스크(Plan 1 Task 8) 시점으로 미룬다.

## Global Constraints

- 모든 작업은 `gender-reveal/web/` 안에서만 한다. `api/`는 수정하지 않는다. 명령은 `gender-reveal/web`에서 `npm`으로 실행한다.
- **정적 export 호환:** 서버 전용 기능(`cookies()`, `headers()`, Route Handler, `next/image` 최적화, `generateStaticParams` 없는 동적 세그먼트, 미들웨어)을 쓰지 않는다. 화면 라우트는 `src/app/g/page.tsx` 하나(동적 세그먼트 없음)다.
- **API 호출은 전부 `src/lib/api.ts`를 거친다.** 상대 경로 `/api/...`, `credentials: 'same-origin'`, GET에는 `cache: 'no-store'`. 컴포넌트가 `fetch`를 직접 부르지 않는다.
- **방명록 메시지·닉네임은 항상 React 텍스트 자식으로만 렌더한다.** `dangerouslySetInnerHTML`, `innerHTML`을 쓰지 않는다(백엔드는 이스케이프하지 않는다).
- **색 사용 원칙(디자인 시스템):** `accent-boy`/`accent-girl`은 결과 화면(`ResultScreen`)에서만 쓴다. 인트로·선택·비밀·만료·축하글 화면은 무채색 + `accent-primary`(CTA)만 쓴다. 모든 버튼/CTA는 `accent-primary`다. 색·간격·반경은 `tailwind.config.ts`에 옮긴 토큰 클래스만 쓰고 임의 hex를 컴포넌트에 쓰지 않는다(일러스트 SVG 자체 제외).
- **display 폰트(Hi Melody)** 는 인트로 문구, 리본/섹션 타이틀, 리빌 타이틀에만. 나머지는 sans(Noto Sans KR).
- **화면 카피는 아래 각 태스크에 적힌 문구를 그대로 쓴다**(고충실도 목업 기준).
- 시간·날짜 표시는 순수 함수(`relativeTime(iso, nowMs)`)로 분리하고 `Date.now()`를 컴포넌트 안에서 직접 호출하지 않는다(props/인자로 주입).
- 접근성: 클릭 가능한 요소는 `<button>`/`<a>`를 쓴다. 이미지에는 의미 있는 `alt`(장식은 `alt=""`)를 준다.
- 테스트는 Vitest + React Testing Library. 각 태스크는 TDD(실패 → 구현 → 통과 → 커밋). 전체 스위트(`npm test`)와 마지막 태스크의 `npm run build`가 통과해야 한다.
- 버전 참고: Next 15.5.x, React 19.x, Tailwind 3.4.x, Vitest 최신. 설치 시 peer 의존성 충돌이 나면 최소 변경으로 해결하고 보고서에 남긴다.

## 파일 구조 요약

```
web/
  package.json  tsconfig.json  next.config.mjs  postcss.config.mjs  tailwind.config.ts
  vitest.config.ts  vitest.setup.ts  .gitignore  README.md
  nginx/gender-reveal.conf
  scripts/make-og.mjs
  public/illustrations/{zodiac,baby,reveal}/*.svg   public/og.png
  src/
    app/        layout.tsx  globals.css  page.tsx  g/page.tsx
    lib/        types.ts  api.ts  time.ts  zodiac.ts  korean.ts  illustrations.ts  result.ts
    hooks/      useTypewriter.ts  usePrefersReducedMotion.ts
    components/ Screen.tsx  Button.tsx  PreReveal.tsx  GuestPage.tsx
                screens/ IntroScreen  SelectScreen  ResultScreen  GuestbookScreen  SecretScreen  ExpiredScreen  (+ *.test.tsx)
```

---

### Task 1: 프로젝트 스캐폴딩 (Next.js 정적 export + Tailwind 토큰 + Vitest)

**Files:**
- Create: `gender-reveal/web/package.json`, `tsconfig.json`, `next.config.mjs`, `postcss.config.mjs`, `tailwind.config.ts`, `vitest.config.ts`, `vitest.setup.ts`, `.gitignore`
- Create: `gender-reveal/web/src/app/layout.tsx`, `src/app/globals.css`, `src/app/page.tsx`
- Create: `gender-reveal/web/src/lib/tokens.test.ts`

**Interfaces:**
- Produces: `@/*` → `src/*` 별칭, Tailwind 토큰 클래스(`bg-surface-50`, `text-ink`, `text-accent-primary`, `bg-accent-boy-soft`, `rounded-radius-md`, `p-space-3`, `font-display`, `text-display-lg` …), 애니메이션 클래스(`animate-float-up`, `animate-shake`, `animate-pop-in`), 폰트 CSS 변수 `--font-hi-melody`/`--font-noto-sans-kr`, 개발용 rewrites(`/api/*`, `/g/:slug`), `npm run dev|build|test|og`

- [ ] **Step 1: 설정 파일 작성**

`gender-reveal/web/package.json`:
```json
{
  "name": "gender-reveal-web",
  "private": true,
  "version": "0.1.0",
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "test": "vitest run",
    "test:watch": "vitest",
    "og": "node scripts/make-og.mjs"
  }
}
```
(의존성은 Step 2의 `npm install`이 채운다.)

`gender-reveal/web/.gitignore`:
```
node_modules/
.next/
out/
next-env.d.ts
*.tsbuildinfo
```

`gender-reveal/web/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "lib": ["dom", "dom.iterable", "esnext"],
    "allowJs": false,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "incremental": true,
    "plugins": [{ "name": "next" }],
    "baseUrl": ".",
    "paths": { "@/*": ["./src/*"] },
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules", "out"]
}
```

`gender-reveal/web/next.config.mjs`:
```js
const isDev = process.env.NODE_ENV === 'development';
const apiOrigin = process.env.API_ORIGIN ?? 'http://localhost:8080';

/**
 * Production: static export served by nginx (see nginx/gender-reveal.conf).
 * Development: `next dev` cannot export, so rewrites stand in for nginx —
 * /api/* goes to Spring, and every /g/<slug> gets the single /g page shell
 * (the slug is read client-side from window.location).
 * @type {import('next').NextConfig}
 */
const nextConfig = isDev
  ? {
      async rewrites() {
        return [
          { source: '/api/:path*', destination: `${apiOrigin}/api/:path*` },
          { source: '/g/:slug', destination: '/g' },
        ];
      },
    }
  : { output: 'export', trailingSlash: false };

export default nextConfig;
```

`gender-reveal/web/postcss.config.mjs`:
```js
export default { plugins: { tailwindcss: {}, autoprefixer: {} } };
```

`gender-reveal/web/vitest.config.ts`:
```ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    css: false,
  },
});
```

`gender-reveal/web/vitest.setup.ts`:
```ts
import '@testing-library/jest-dom/vitest';
```

`gender-reveal/web/tailwind.config.ts` (토큰은 `planning/design-system/project/tokens.json`에서 옮긴 것 — 일러스트 전용 `illus-*` 토큰은 SVG 내부에서만 쓰므로 옮기지 않는다):
```ts
import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        'surface-50': '#FFFBF6',
        'surface-100': '#FFFFFF',
        'surface-200': '#F3EEE7',
        border: '#E6E0D5',
        ink: '#2E2B27',
        'ink-muted': '#8C8579',
        'accent-boy': '#6F86E6',
        'accent-boy-soft': '#EEF1FD',
        'accent-girl': '#E37AA6',
        'accent-girl-soft': '#FDEDF4',
        'accent-primary': '#F2793A',
        'accent-primary-soft': '#FDEAD9',
        danger: '#D6483C',
      },
      fontFamily: {
        display: ['var(--font-hi-melody)', 'system-ui', 'sans-serif'],
        sans: ['var(--font-noto-sans-kr)', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        'display-lg': ['32px', { lineHeight: '40px', fontWeight: '400' }],
        'display-md': ['22px', { lineHeight: '30px', fontWeight: '400' }],
        'body-lg': ['16px', { lineHeight: '24px', fontWeight: '500' }],
        body: ['14px', { lineHeight: '22px', fontWeight: '400' }],
        'body-sm': ['12px', { lineHeight: '18px', fontWeight: '400' }],
        label: ['13px', { lineHeight: '18px', fontWeight: '600' }],
      },
      spacing: {
        'space-1': '4px',
        'space-2': '8px',
        'space-3': '16px',
        'space-4': '24px',
        'space-5': '32px',
      },
      borderRadius: {
        'radius-sm': '8px',
        'radius-md': '14px',
        'radius-full': '999px',
      },
      keyframes: {
        'float-up': {
          '0%': { transform: 'translateY(0) scale(1)', opacity: '0' },
          '20%': { opacity: '0.7' },
          '100%': { transform: 'translateY(-120px) scale(1.2)', opacity: '0' },
        },
        shake: {
          '0%, 100%': { transform: 'rotate(0deg)' },
          '20%': { transform: 'rotate(-6deg)' },
          '40%': { transform: 'rotate(6deg)' },
          '60%': { transform: 'rotate(-4deg)' },
          '80%': { transform: 'rotate(4deg)' },
        },
        'pop-in': {
          '0%': { transform: 'scale(0.3)', opacity: '0' },
          '70%': { transform: 'scale(1.1)', opacity: '1' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
      },
      animation: {
        'float-up': 'float-up 4s ease-in infinite',
        shake: 'shake 0.9s ease-in-out',
        'pop-in': 'pop-in 0.7s ease-out both',
      },
    },
  },
  plugins: [],
};

export default config;
```

- [ ] **Step 2: 의존성 설치**

```bash
cd gender-reveal/web
npm install next@^15.5 react@^19 react-dom@^19
npm install -D typescript@^5 @types/node@^22 @types/react@^19 @types/react-dom@^19 tailwindcss@^3.4 postcss autoprefixer vitest @vitejs/plugin-react jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event sharp
```
예상: `package-lock.json` 생성, 오류 없음. `node_modules/`는 커밋하지 않는다.

- [ ] **Step 3: 실패하는 테스트 작성**

`gender-reveal/web/src/lib/tokens.test.ts`:
```ts
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
});
```

- [ ] **Step 4: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 이 시점엔 설정 파일이 이미 있으므로 통과할 수도 있다. 그 경우 "RED를 만들 수 없다(설정 태스크)"고 보고서에 적고 다음으로 진행한다. (테스트를 만든 뒤 `tailwind.config.ts`의 `accent-primary` 값을 잠깐 바꿔 실패를 확인하고 되돌려도 된다.)

- [ ] **Step 5: 앱 셸 작성**

`gender-reveal/web/src/app/globals.css`:
```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  html {
    background-color: #fffbf6;
  }
  body {
    @apply bg-surface-50 font-sans text-body text-ink antialiased;
  }
}
```

`gender-reveal/web/src/app/layout.tsx`:
```tsx
import type { Metadata } from 'next';
import { Hi_Melody, Noto_Sans_KR } from 'next/font/google';
import './globals.css';

const hiMelody = Hi_Melody({
  weight: '400',
  variable: '--font-hi-melody',
  display: 'swap',
  preload: false,
});

const notoSansKr = Noto_Sans_KR({
  weight: ['400', '500', '600'],
  variable: '--font-noto-sans-kr',
  display: 'swap',
  preload: false,
});

// Static export = one OG card for every shared link, so nothing here may depend on a page
// (and must never hint at gender or reveal state). NEXT_PUBLIC_SITE_URL must be the public
// origin at build time so og:image resolves to an absolute URL for crawlers.
const siteUrl = process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000';
const title = '젠더리빌 — 우리 아기는 딸일까요, 아들일까요?';
const description = '아기의 성별을 맞춰보고 함께 축하해 주세요.';

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title,
  description,
  openGraph: {
    title,
    description,
    type: 'website',
    locale: 'ko_KR',
    images: [{ url: '/og.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', title, description, images: ['/og.png'] },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko" className={`${hiMelody.variable} ${notoSansKr.variable}`}>
      <body>{children}</body>
    </html>
  );
}
```

`gender-reveal/web/src/app/page.tsx`:
```tsx
export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-[420px] flex-col items-center justify-center gap-space-3 p-space-4 text-center">
      <h1 className="font-display text-display-lg">젠더리빌</h1>
      <p className="text-body text-ink-muted">우리 아기는 딸일까요, 아들일까요?</p>
    </main>
  );
}
```

- [ ] **Step 6: 통과 확인**

실행: `cd gender-reveal/web && npm test && npm run build`
예상: 테스트 PASS, 빌드 성공(`out/index.html` 생성). 폰트 다운로드가 네트워크 오류로 실패하면 보고서에 남기고 BLOCKED로 보고한다(재시도 1회 허용).

- [ ] **Step 7: 커밋**

```bash
cd gender-reveal
git add web/package.json web/package-lock.json web/tsconfig.json web/next.config.mjs web/postcss.config.mjs web/tailwind.config.ts web/vitest.config.ts web/vitest.setup.ts web/.gitignore web/src
git commit -m "feat(web): Next.js 정적 export 프로젝트 스캐폴딩(Tailwind 토큰, Vitest)"
```

---

### Task 2: 일러스트 자산 복사 + 띠 계산 + 한국어 조사 유틸

**Files:**
- Create: `gender-reveal/web/public/illustrations/zodiac/*.svg` (12), `baby/*.svg` (2), `reveal/*.svg` (6)
- Create: `gender-reveal/web/src/lib/zodiac.ts`, `korean.ts`, `illustrations.ts`
- Create: `gender-reveal/web/src/lib/zodiac.test.ts`, `korean.test.ts`, `illustrations.test.ts`

**Interfaces:**
- Consumes: 디자인 시스템 SVG(`planning/design-system/project/assets/`)
- Produces: `type ZodiacKey`, `zodiacFromYear(year): ZodiacKey`, `zodiacFromDueDate(dueDate: string | null | undefined): ZodiacKey | null`, `zodiacLabelKo(key): string`(예: `'말띠'`), `topic(word): string`(은/는 붙이기), `hasFinalConsonant(word): boolean`, `zodiacSrc(key)`, `babySrc(gender)`, `revealSrc(theme, gender)`

- [ ] **Step 1: 자산 복사 (정확한 매핑)**

```bash
cd gender-reveal
A=planning/design-system/project/assets
D=web/public/illustrations
mkdir -p $D/zodiac $D/baby $D/reveal
cp "$A/12지신/쥐.svg" $D/zodiac/rat.svg
cp "$A/12지신/소.svg" $D/zodiac/ox.svg
cp "$A/12지신/호랑이.svg" $D/zodiac/tiger.svg
cp "$A/12지신/토끼.svg" $D/zodiac/rabbit.svg
cp "$A/12지신/용.svg" $D/zodiac/dragon.svg
cp "$A/12지신/뱀.svg" $D/zodiac/snake.svg
cp "$A/12지신/말.svg" $D/zodiac/horse.svg
cp "$A/12지신/양.svg" $D/zodiac/sheep.svg
cp "$A/12지신/원숭이.svg" $D/zodiac/monkey.svg
cp "$A/12지신/닭.svg" $D/zodiac/rooster.svg
cp "$A/12지신/개.svg" $D/zodiac/dog.svg
cp "$A/12지신/돼지.svg" $D/zodiac/pig.svg
cp "$A/캐릭터/아기-남아.svg" $D/baby/boy.svg
cp "$A/캐릭터/아기-여아.svg" $D/baby/girl.svg
cp "$A/리빌테마/서프라이즈박스-남아.svg" $D/reveal/box-boy.svg
cp "$A/리빌테마/서프라이즈박스-여아.svg" $D/reveal/box-girl.svg
cp "$A/리빌테마/케이크-남아.svg" $D/reveal/cake-boy.svg
cp "$A/리빌테마/케이크-여아.svg" $D/reveal/cake-girl.svg
cp "$A/리빌테마/풍선-남아.svg" $D/reveal/balloon-boy.svg
cp "$A/리빌테마/풍선-여아.svg" $D/reveal/balloon-girl.svg
```
(셸 인자에 한글 경로가 있으므로 각 줄을 별도 명령으로 실행한다.)

- [ ] **Step 2: 실패하는 테스트 작성**

`gender-reveal/web/src/lib/zodiac.test.ts`:
```ts
import { zodiacFromDueDate, zodiacFromYear, zodiacLabelKo } from './zodiac';

describe('zodiacFromYear', () => {
  it.each([
    [2020, 'rat'],
    [2021, 'ox'],
    [2022, 'tiger'],
    [2023, 'rabbit'],
    [2024, 'dragon'],
    [2025, 'snake'],
    [2026, 'horse'],
    [2027, 'sheep'],
    [2028, 'monkey'],
    [2029, 'rooster'],
    [2030, 'dog'],
    [2031, 'pig'],
    [2032, 'rat'],
  ])('%i is %s', (year, expected) => {
    expect(zodiacFromYear(year)).toBe(expected);
  });
});

describe('zodiacFromDueDate', () => {
  it('reads the year from an ISO date', () => {
    expect(zodiacFromDueDate('2026-11-03')).toBe('horse');
  });

  it('returns null when there is no usable due date', () => {
    expect(zodiacFromDueDate(null)).toBeNull();
    expect(zodiacFromDueDate(undefined)).toBeNull();
    expect(zodiacFromDueDate('')).toBeNull();
    expect(zodiacFromDueDate('not-a-date')).toBeNull();
  });
});

describe('zodiacLabelKo', () => {
  it('appends 띠 to the animal name', () => {
    expect(zodiacLabelKo('horse')).toBe('말띠');
    expect(zodiacLabelKo('rooster')).toBe('닭띠');
    expect(zodiacLabelKo('pig')).toBe('돼지띠');
  });
});
```

`gender-reveal/web/src/lib/korean.test.ts`:
```ts
import { hasFinalConsonant, topic } from './korean';

describe('hasFinalConsonant', () => {
  it('detects a trailing consonant (받침)', () => {
    expect(hasFinalConsonant('별')).toBe(true);
    expect(hasFinalConsonant('뽀튼이')).toBe(false);
  });

  it('is false for empty or non-Hangul input', () => {
    expect(hasFinalConsonant('')).toBe(false);
    expect(hasFinalConsonant('Jun')).toBe(false);
  });
});

describe('topic', () => {
  it('attaches 은/는 by final consonant', () => {
    expect(topic('뽀튼이')).toBe('뽀튼이는');
    expect(topic('별')).toBe('별은');
  });
});
```

`gender-reveal/web/src/lib/illustrations.test.ts`:
```ts
import fs from 'node:fs';
import path from 'node:path';
import { babySrc, revealSrc, zodiacSrc } from './illustrations';
import { ZODIAC_KEYS } from './zodiac';

const publicDir = path.resolve(__dirname, '../../public');
const exists = (src: string) => fs.existsSync(path.join(publicDir, src));

describe('illustration paths', () => {
  it('has a file for every zodiac animal', () => {
    for (const key of ZODIAC_KEYS) {
      expect(exists(zodiacSrc(key))).toBe(true);
    }
  });

  it('has baby art for both genders', () => {
    expect(exists(babySrc('boy'))).toBe(true);
    expect(exists(babySrc('girl'))).toBe(true);
  });

  it('has reveal art for every theme and gender', () => {
    for (const theme of ['box', 'cake', 'balloon'] as const) {
      for (const gender of ['boy', 'girl'] as const) {
        expect(exists(revealSrc(theme, gender))).toBe(true);
      }
    }
  });
});
```

- [ ] **Step 3: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 4: 구현**

`gender-reveal/web/src/lib/zodiac.ts`:
```ts
export type ZodiacKey =
  | 'rat' | 'ox' | 'tiger' | 'rabbit' | 'dragon' | 'snake'
  | 'horse' | 'sheep' | 'monkey' | 'rooster' | 'dog' | 'pig';

// Index = year % 12 (2020 % 12 === 4 → rat). Gregorian-year based; the lunar new year boundary is ignored.
const BY_YEAR_MOD_12: ZodiacKey[] = [
  'monkey', 'rooster', 'dog', 'pig', 'rat', 'ox',
  'tiger', 'rabbit', 'dragon', 'snake', 'horse', 'sheep',
];

export const ZODIAC_KEYS: ZodiacKey[] = [
  'rat', 'ox', 'tiger', 'rabbit', 'dragon', 'snake',
  'horse', 'sheep', 'monkey', 'rooster', 'dog', 'pig',
];

const LABEL_KO: Record<ZodiacKey, string> = {
  rat: '쥐', ox: '소', tiger: '호랑이', rabbit: '토끼', dragon: '용', snake: '뱀',
  horse: '말', sheep: '양', monkey: '원숭이', rooster: '닭', dog: '개', pig: '돼지',
};

export function zodiacFromYear(year: number): ZodiacKey {
  return BY_YEAR_MOD_12[((year % 12) + 12) % 12];
}

export function zodiacFromDueDate(dueDate: string | null | undefined): ZodiacKey | null {
  if (!dueDate) return null;
  const match = /^(\d{4})-\d{2}-\d{2}$/.exec(dueDate);
  if (!match) return null;
  return zodiacFromYear(Number(match[1]));
}

export function zodiacLabelKo(key: ZodiacKey): string {
  return `${LABEL_KO[key]}띠`;
}
```

`gender-reveal/web/src/lib/korean.ts`:
```ts
const HANGUL_START = 0xac00;
const HANGUL_END = 0xd7a3;

export function hasFinalConsonant(word: string): boolean {
  const last = word.trim().slice(-1);
  if (!last) return false;
  const code = last.charCodeAt(0);
  if (code < HANGUL_START || code > HANGUL_END) return false;
  return (code - HANGUL_START) % 28 !== 0;
}

/** Attaches the topic particle 은/는 to a word ("뽀튼이" → "뽀튼이는", "별" → "별은"). */
export function topic(word: string): string {
  return word + (hasFinalConsonant(word) ? '은' : '는');
}
```

`gender-reveal/web/src/lib/illustrations.ts`:
```ts
import type { ZodiacKey } from './zodiac';
import type { Gender, Theme } from './types';

export const zodiacSrc = (key: ZodiacKey) => `/illustrations/zodiac/${key}.svg`;
export const babySrc = (gender: Gender) => `/illustrations/baby/${gender}.svg`;
export const revealSrc = (theme: Theme, gender: Gender) => `/illustrations/reveal/${theme}-${gender}.svg`;
```

`gender-reveal/web/src/lib/types.ts` (Task 3에서 이어서 확장하지만 이 태스크에서 먼저 만든다):
```ts
export type Gender = 'boy' | 'girl';
export type Theme = 'box' | 'cake' | 'balloon';
```

- [ ] **Step 5: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 6: 커밋**

```bash
cd gender-reveal
git add web/public/illustrations web/src/lib/zodiac.ts web/src/lib/zodiac.test.ts web/src/lib/korean.ts web/src/lib/korean.test.ts web/src/lib/illustrations.ts web/src/lib/illustrations.test.ts web/src/lib/types.ts
git commit -m "feat(web): 일러스트 자산 복사와 띠 계산·한국어 조사 유틸 추가"
```

---

### Task 3: 타입, API 클라이언트, 상대 시간 유틸

**Files:**
- Modify: `gender-reveal/web/src/lib/types.ts`
- Create: `gender-reveal/web/src/lib/api.ts`, `time.ts`
- Create: `gender-reveal/web/src/lib/api.test.ts`, `time.test.ts`

**Interfaces:**
- Consumes: 백엔드 계약(현재 코드 기준)
  - `GET /api/pages/{slug}` → 200 `{status, nickname, actualGender, dueDate, message, theme, bgmEnabled}` (`secret`/`expired`에서는 `status`·`nickname` 외 필드가 `null`이거나 없음), 404 `{error}`
  - `POST /api/pages/{slug}/guess` `{guessedGender: "boy"|"girl"}` → 201 `{guessedGender, createdAt}`, 409 `{error, guessedGender}`(이미 맞춤) 또는 409 `{error}`(페이지가 open이 아님), 404
  - `GET /api/pages/{slug}/guestbook` → 200 `[{nickname, message, createdAt}]`(숨김 제외, 최신순), 409 `{error}`(open 아님)
  - `POST /api/pages/{slug}/guestbook` `{nickname(≤40), message(≤500)}` → 201 `{nickname, message, createdAt}`, 400(검증), 409
- Produces: `PageView`(secret/expired/open 유니온), `parsePageView(raw)`, `GuestbookEntry`, `ApiError(status, body)`, `getPage(slug)`, `submitGuess(slug, gender): Promise<{guessedGender: Gender; alreadyGuessed: boolean}>`, `getGuestbook(slug)`, `postGuestbook(slug, {nickname, message})`, `relativeTime(iso, nowMs): string`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/lib/time.test.ts`:
```ts
import { relativeTime } from './time';

const NOW = Date.parse('2026-09-21T12:00:00.000Z');
const ago = (ms: number) => new Date(NOW - ms).toISOString();

describe('relativeTime', () => {
  it('says 방금 전 under a minute', () => {
    expect(relativeTime(ago(10_000), NOW)).toBe('방금 전');
  });

  it('counts minutes, hours and days', () => {
    expect(relativeTime(ago(5 * 60_000), NOW)).toBe('5분 전');
    expect(relativeTime(ago(3 * 3_600_000), NOW)).toBe('3시간 전');
    expect(relativeTime(ago(26 * 3_600_000), NOW)).toBe('1일 전');
    expect(relativeTime(ago(10 * 86_400_000), NOW)).toBe('10일 전');
  });

  it('treats future or invalid timestamps as 방금 전', () => {
    expect(relativeTime(new Date(NOW + 60_000).toISOString(), NOW)).toBe('방금 전');
    expect(relativeTime('garbage', NOW)).toBe('방금 전');
  });
});
```

`gender-reveal/web/src/lib/api.test.ts`:
```ts
import { ApiError, getGuestbook, getPage, postGuestbook, submitGuess } from './api';

function respond(status: number, body?: unknown) {
  return Promise.resolve(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
}

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('getPage', () => {
  it('parses an open page', async () => {
    fetchMock.mockReturnValue(
      respond(200, {
        status: 'open', nickname: '뽀튼이', actualGender: 'boy', dueDate: '2026-11-03',
        message: '환영해요', theme: 'cake', bgmEnabled: false,
      }),
    );

    const view = await getPage('my-slug');

    expect(view).toEqual({
      status: 'open', nickname: '뽀튼이', actualGender: 'boy', dueDate: '2026-11-03',
      message: '환영해요', theme: 'cake',
    });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/pages/my-slug');
    expect(init).toMatchObject({ cache: 'no-store', credentials: 'same-origin' });
  });

  it('drops the gender-bearing fields of secret and expired pages', async () => {
    fetchMock.mockReturnValueOnce(respond(200, { status: 'secret', nickname: '뽀튼이', actualGender: null }));
    fetchMock.mockReturnValueOnce(respond(200, { status: 'expired', nickname: '뽀튼이' }));

    expect(await getPage('a')).toEqual({ status: 'secret', nickname: '뽀튼이' });
    expect(await getPage('b')).toEqual({ status: 'expired', nickname: '뽀튼이' });
  });

  it('encodes the slug', async () => {
    fetchMock.mockReturnValue(respond(200, { status: 'secret', nickname: 'x' }));

    await getPage('a b/c');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/pages/a%20b%2Fc');
  });

  it('throws ApiError with status and body on failure', async () => {
    fetchMock.mockReturnValue(respond(404, { error: 'Page not found: nope' }));

    await expect(getPage('nope')).rejects.toMatchObject({ status: 404, body: { error: 'Page not found: nope' } });
    await expect(getPage('nope')).rejects.toBeInstanceOf(ApiError);
  });

  it('rejects an unknown status value', async () => {
    fetchMock.mockReturnValue(respond(200, { status: 'weird', nickname: 'x' }));

    await expect(getPage('x')).rejects.toThrow();
  });
});

describe('submitGuess', () => {
  it('posts the guess as JSON and reports a fresh guess', async () => {
    fetchMock.mockReturnValue(respond(201, { guessedGender: 'girl', createdAt: '2026-09-21T00:00:00.000Z' }));

    const result = await submitGuess('s', 'girl');

    expect(result).toEqual({ guessedGender: 'girl', alreadyGuessed: false });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/pages/s/guess');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ guessedGender: 'girl' });
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json' });
  });

  it('turns a duplicate-guess 409 into the earlier guess', async () => {
    fetchMock.mockReturnValue(respond(409, { error: 'dup', guessedGender: 'boy' }));

    expect(await submitGuess('s', 'girl')).toEqual({ guessedGender: 'boy', alreadyGuessed: true });
  });

  it('rethrows a 409 that carries no earlier guess (page not open)', async () => {
    fetchMock.mockReturnValue(respond(409, { error: 'Page is not open' }));

    await expect(submitGuess('s', 'girl')).rejects.toMatchObject({ status: 409 });
  });
});

describe('guestbook calls', () => {
  it('lists entries', async () => {
    const entries = [{ nickname: '이모', message: '축하해요', createdAt: '2026-09-21T00:00:00.000Z' }];
    fetchMock.mockReturnValue(respond(200, entries));

    expect(await getGuestbook('s')).toEqual(entries);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/pages/s/guestbook');
  });

  it('posts a new entry', async () => {
    const created = { nickname: '이모', message: '축하해요', createdAt: '2026-09-21T00:00:00.000Z' };
    fetchMock.mockReturnValue(respond(201, created));

    expect(await postGuestbook('s', { nickname: '이모', message: '축하해요' })).toEqual(created);
    const init = fetchMock.mock.calls[0][1];
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ nickname: '이모', message: '축하해요' });
  });

  it('surfaces validation failures as ApiError 400', async () => {
    fetchMock.mockReturnValue(respond(400, { errors: ['nickname'] }));

    await expect(postGuestbook('s', { nickname: '', message: '' })).rejects.toMatchObject({ status: 400 });
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/lib/types.ts` 전체를 다음으로 교체:
```ts
export type Gender = 'boy' | 'girl';
export type Theme = 'box' | 'cake' | 'balloon';

export type OpenPageView = {
  status: 'open';
  nickname: string;
  actualGender: Gender;
  dueDate: string | null;
  message: string | null;
  theme: Theme;
};

export type PageView =
  | { status: 'secret'; nickname: string }
  | { status: 'expired'; nickname: string }
  | OpenPageView;

export type GuestbookEntry = {
  nickname: string;
  message: string;
  createdAt: string;
};

/** Validates the API payload; secret/expired pages must never carry gender-bearing fields into the UI. */
export function parsePageView(raw: unknown): PageView {
  if (typeof raw !== 'object' || raw === null) throw new Error('Unexpected page payload');
  const r = raw as Record<string, unknown>;
  const nickname = typeof r.nickname === 'string' ? r.nickname : '';
  if (r.status === 'secret') return { status: 'secret', nickname };
  if (r.status === 'expired') return { status: 'expired', nickname };
  if (r.status === 'open') {
    if (r.actualGender !== 'boy' && r.actualGender !== 'girl') throw new Error('Unexpected page payload');
    if (r.theme !== 'box' && r.theme !== 'cake' && r.theme !== 'balloon') throw new Error('Unexpected page payload');
    return {
      status: 'open',
      nickname,
      actualGender: r.actualGender,
      dueDate: typeof r.dueDate === 'string' ? r.dueDate : null,
      message: typeof r.message === 'string' && r.message.trim() ? r.message : null,
      theme: r.theme,
    };
  }
  throw new Error('Unexpected page status');
}
```

`gender-reveal/web/src/lib/api.ts`:
```ts
import { parsePageView } from './types';
import type { Gender, GuestbookEntry, PageView } from './types';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: unknown,
  ) {
    super(`API request failed with status ${status}`);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    headers: { Accept: 'application/json', ...init.headers },
  });
  if (!response.ok) {
    let body: unknown = null;
    try {
      body = await response.json();
    } catch {
      // Non-JSON error body: keep null.
    }
    throw new ApiError(response.status, body);
  }
  return (await response.json()) as T;
}

const pagePath = (slug: string) => `/api/pages/${encodeURIComponent(slug)}`;

function postJson<T>(path: string, payload: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export async function getPage(slug: string): Promise<PageView> {
  return parsePageView(await request<unknown>(pagePath(slug), { cache: 'no-store' }));
}

/**
 * Posts a guess. A duplicate (409 carrying the earlier `guessedGender`) is not an error for the UI:
 * the guest simply proceeds with the guess they already made. A 409 without one means the page is
 * not open and is rethrown.
 */
export async function submitGuess(
  slug: string,
  gender: Gender,
): Promise<{ guessedGender: Gender; alreadyGuessed: boolean }> {
  try {
    const created = await postJson<{ guessedGender: Gender }>(`${pagePath(slug)}/guess`, { guessedGender: gender });
    return { guessedGender: created.guessedGender, alreadyGuessed: false };
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      const earlier = (error.body as { guessedGender?: unknown } | null)?.guessedGender;
      if (earlier === 'boy' || earlier === 'girl') {
        return { guessedGender: earlier, alreadyGuessed: true };
      }
    }
    throw error;
  }
}

export function getGuestbook(slug: string): Promise<GuestbookEntry[]> {
  return request<GuestbookEntry[]>(`${pagePath(slug)}/guestbook`, { cache: 'no-store' });
}

export function postGuestbook(
  slug: string,
  entry: { nickname: string; message: string },
): Promise<GuestbookEntry> {
  return postJson<GuestbookEntry>(`${pagePath(slug)}/guestbook`, entry);
}
```

`gender-reveal/web/src/lib/time.ts`:
```ts
const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export function relativeTime(iso: string, nowMs: number): string {
  const then = Date.parse(iso);
  if (Number.isNaN(then)) return '방금 전';
  const diff = nowMs - then;
  if (diff < MINUTE) return '방금 전';
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}분 전`;
  if (diff < DAY) return `${Math.floor(diff / HOUR)}시간 전`;
  return `${Math.floor(diff / DAY)}일 전`;
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/lib
git commit -m "feat(web): API 클라이언트, 페이지 응답 파서, 상대 시간 유틸 추가"
```

---

### Task 4: 공통 레이아웃/버튼 + 훅 + 인트로·비밀·만료 화면

**Files:**
- Create: `gender-reveal/web/src/components/Screen.tsx`, `Button.tsx`
- Create: `gender-reveal/web/src/hooks/useTypewriter.ts`, `usePrefersReducedMotion.ts`
- Create: `gender-reveal/web/src/components/screens/IntroScreen.tsx`, `SecretScreen.tsx`, `ExpiredScreen.tsx`
- Create: `gender-reveal/web/src/hooks/useTypewriter.test.ts`
- Create: `gender-reveal/web/src/components/screens/IntroScreen.test.tsx`, `SecretScreen.test.tsx`, `ExpiredScreen.test.tsx`

**Interfaces:**
- Consumes: `topic`(Task 2), `zodiacSrc`/`zodiacLabelKo`/`ZodiacKey`(Task 2)
- Produces: `<Screen>`(폭 420px 중앙 정렬 래퍼, `className`/`children`), `<Button>`(`accent-primary` CTA, `onClick`/`disabled`/`children`), `useTypewriter(text, {speedMs?, instant?}) → {shown, done}`, `usePrefersReducedMotion(): boolean`, `<IntroScreen nickname zodiac onNext autoAdvanceMs? />`, `<SecretScreen nickname />`, `<ExpiredScreen />`

**카피(그대로 사용):**
- 인트로: 타이핑 문구 `두근두근...` / `{닉네임+은·는} 딸일까요,` / `아들일까요?`(줄바꿈으로 구분), 하단 버튼 `탭해서 계속하기`, 5초 후 자동 전환
- 비밀: `Coming Soon` / `아직 {닉네임}의 성별은` / `비밀이에요` / `공개 시간이 되면 이 페이지가` / `바뀔 예정이에요`
- 만료: `열람이 제한된 페이지예요` / `보관주기(30일)가 지나 더 이상` / `페이지를 볼 수 없어요` / `데이터는 삭제되지 않았어요 — 소유자가` / `로그인하면 1회에 한해 30일 연장할 수 있어요` / 링크 `소유자 로그인 (매직링크)` → `/login`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/hooks/useTypewriter.test.ts`:
```ts
import { act, renderHook } from '@testing-library/react';
import { useTypewriter } from './useTypewriter';

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('useTypewriter', () => {
  it('reveals the text one character per tick and reports done', () => {
    const { result } = renderHook(() => useTypewriter('abc', { speedMs: 50 }));

    expect(result.current).toEqual({ shown: '', done: false });
    act(() => { vi.advanceTimersByTime(50); });
    expect(result.current.shown).toBe('a');
    act(() => { vi.advanceTimersByTime(100); });
    expect(result.current).toEqual({ shown: 'abc', done: true });
  });

  it('shows everything at once when instant', () => {
    const { result } = renderHook(() => useTypewriter('abc', { instant: true }));

    expect(result.current).toEqual({ shown: 'abc', done: true });
  });
});
```

`gender-reveal/web/src/components/screens/IntroScreen.test.tsx`:
```tsx
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { IntroScreen } from './IntroScreen';

beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
afterEach(() => vi.useRealTimers());

describe('IntroScreen', () => {
  it('types the question with the right topic particle', () => {
    render(<IntroScreen nickname="뽀튼이" zodiac={null} onNext={() => {}} />);

    act(() => { vi.advanceTimersByTime(3000); });

    const text = screen.getByTestId('intro-text');
    expect(text.textContent).toBe('두근두근...\n뽀튼이는 딸일까요,\n아들일까요?');
  });

  it('uses 은 after a final consonant', () => {
    render(<IntroScreen nickname="별" zodiac={null} onNext={() => {}} />);

    act(() => { vi.advanceTimersByTime(3000); });

    expect(screen.getByTestId('intro-text').textContent).toContain('별은 딸일까요,');
  });

  it('shows the zodiac character only when a zodiac is given', () => {
    const { rerender } = render(<IntroScreen nickname="뽀튼이" zodiac={null} onNext={() => {}} />);
    expect(screen.queryByRole('img', { name: '말띠' })).not.toBeInTheDocument();

    rerender(<IntroScreen nickname="뽀튼이" zodiac="horse" onNext={() => {}} />);
    expect(screen.getByRole('img', { name: '말띠' })).toHaveAttribute('src', '/illustrations/zodiac/horse.svg');
  });

  it('advances when the continue button is pressed', async () => {
    const onNext = vi.fn();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<IntroScreen nickname="뽀튼이" zodiac={null} onNext={onNext} />);

    await user.click(screen.getByRole('button', { name: '탭해서 계속하기' }));

    expect(onNext).toHaveBeenCalledTimes(1);
  });

  it('advances by itself after the auto-advance delay', () => {
    const onNext = vi.fn();
    render(<IntroScreen nickname="뽀튼이" zodiac={null} onNext={onNext} autoAdvanceMs={5000} />);

    act(() => { vi.advanceTimersByTime(4999); });
    expect(onNext).not.toHaveBeenCalled();
    act(() => { vi.advanceTimersByTime(1); });
    expect(onNext).toHaveBeenCalledTimes(1);
  });
});
```

`gender-reveal/web/src/components/screens/SecretScreen.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react';
import { SecretScreen } from './SecretScreen';

describe('SecretScreen', () => {
  it('teases without hinting at gender', () => {
    render(<SecretScreen nickname="뽀튼이" />);

    expect(screen.getByText('Coming Soon')).toBeInTheDocument();
    expect(screen.getByText('아직 뽀튼이의 성별은')).toBeInTheDocument();
    expect(screen.getByText('비밀이에요')).toBeInTheDocument();
    expect(screen.getByText('공개 시간이 되면 이 페이지가')).toBeInTheDocument();
    expect(screen.getByText('바뀔 예정이에요')).toBeInTheDocument();
  });
});
```

`gender-reveal/web/src/components/screens/ExpiredScreen.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react';
import { ExpiredScreen } from './ExpiredScreen';

describe('ExpiredScreen', () => {
  it('explains the restriction and links owners to login', () => {
    render(<ExpiredScreen />);

    expect(screen.getByText('열람이 제한된 페이지예요')).toBeInTheDocument();
    expect(screen.getByText('보관주기(30일)가 지나 더 이상')).toBeInTheDocument();
    expect(screen.getByText('데이터는 삭제되지 않았어요 — 소유자가')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '소유자 로그인 (매직링크)' })).toHaveAttribute('href', '/login');
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/hooks/useTypewriter.ts`:
```ts
'use client';

import { useEffect, useState } from 'react';

export function useTypewriter(
  text: string,
  { speedMs = 70, instant = false }: { speedMs?: number; instant?: boolean } = {},
): { shown: string; done: boolean } {
  const [count, setCount] = useState(instant ? text.length : 0);

  useEffect(() => {
    if (instant) {
      setCount(text.length);
      return;
    }
    setCount(0);
    let i = 0;
    const id = setInterval(() => {
      i += 1;
      setCount(i);
      if (i >= text.length) clearInterval(id);
    }, speedMs);
    return () => clearInterval(id);
  }, [text, speedMs, instant]);

  return { shown: text.slice(0, count), done: count >= text.length };
}
```

`gender-reveal/web/src/hooks/usePrefersReducedMotion.ts`:
```ts
'use client';

import { useEffect, useState } from 'react';

export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    const query = window.matchMedia?.('(prefers-reduced-motion: reduce)');
    if (!query) return;
    setReduced(query.matches);
    const onChange = (event: MediaQueryListEvent) => setReduced(event.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  return reduced;
}
```

`gender-reveal/web/src/components/Screen.tsx`:
```tsx
import type { ReactNode } from 'react';

export function Screen({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <main
      className={`relative mx-auto flex min-h-screen w-full max-w-[420px] flex-col items-center justify-center gap-space-4 overflow-hidden px-space-3 py-space-5 text-center ${className}`}
    >
      {children}
    </main>
  );
}
```

`gender-reveal/web/src/components/Button.tsx`:
```tsx
import type { ButtonHTMLAttributes } from 'react';

export function Button({ className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      className={`rounded-radius-full bg-accent-primary px-space-4 py-space-2 text-label text-surface-100 transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
      {...props}
    />
  );
}
```

`gender-reveal/web/src/components/screens/IntroScreen.tsx`:
```tsx
'use client';

import { useEffect } from 'react';
import { Button } from '../Button';
import { Screen } from '../Screen';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import { useTypewriter } from '@/hooks/useTypewriter';
import { zodiacSrc } from '@/lib/illustrations';
import { topic } from '@/lib/korean';
import { zodiacLabelKo } from '@/lib/zodiac';
import type { ZodiacKey } from '@/lib/zodiac';

const PARTICLES = [8, 22, 36, 50, 64, 78, 90];

export function IntroScreen({
  nickname,
  zodiac,
  onNext,
  autoAdvanceMs = 5000,
}: {
  nickname: string;
  zodiac: ZodiacKey | null;
  onNext: () => void;
  autoAdvanceMs?: number;
}) {
  const reduced = usePrefersReducedMotion();
  const text = `두근두근...\n${topic(nickname)} 딸일까요,\n아들일까요?`;
  const { shown } = useTypewriter(text, { instant: reduced });

  useEffect(() => {
    const id = setTimeout(onNext, autoAdvanceMs);
    return () => clearTimeout(id);
  }, [onNext, autoAdvanceMs]);

  return (
    <Screen>
      <div aria-hidden className="pointer-events-none absolute inset-0">
        {PARTICLES.map((left, i) => (
          <span
            key={left}
            className="absolute bottom-24 font-display text-display-md text-ink-muted opacity-0 motion-safe:animate-float-up"
            style={{ left: `${left}%`, animationDelay: `${i * 0.6}s` }}
          >
            ?
          </span>
        ))}
      </div>
      {zodiac && (
        <img src={zodiacSrc(zodiac)} alt={zodiacLabelKo(zodiac)} className="h-40 w-40" />
      )}
      <p data-testid="intro-text" className="min-h-[120px] whitespace-pre-line font-display text-display-lg">
        {shown}
      </p>
      <Button onClick={onNext}>탭해서 계속하기</Button>
    </Screen>
  );
}
```

`gender-reveal/web/src/components/screens/SecretScreen.tsx`:
```tsx
import { Screen } from '../Screen';

export function SecretScreen({ nickname }: { nickname: string }) {
  return (
    <Screen>
      <svg aria-hidden viewBox="0 0 120 120" className="h-28 w-28 text-ink-muted">
        <rect x="20" y="52" width="80" height="56" rx="8" fill="#F3EEE7" stroke="currentColor" strokeWidth="3" />
        <rect x="14" y="38" width="92" height="20" rx="8" fill="#FFFFFF" stroke="currentColor" strokeWidth="3" />
        <path d="M60 38v70M60 38c-14-20-32-8-20 0M60 38c14-20 32-8 20 0" fill="none" stroke="currentColor" strokeWidth="3" />
      </svg>
      <p className="font-display text-display-md text-ink-muted">Coming Soon</p>
      <div className="font-display text-display-lg">
        <p>아직 {nickname}의 성별은</p>
        <p>비밀이에요</p>
      </div>
      <div className="text-body text-ink-muted">
        <p>공개 시간이 되면 이 페이지가</p>
        <p>바뀔 예정이에요</p>
      </div>
    </Screen>
  );
}
```

`gender-reveal/web/src/components/screens/ExpiredScreen.tsx`:
```tsx
import { Screen } from '../Screen';

export function ExpiredScreen() {
  return (
    <Screen>
      <svg aria-hidden viewBox="0 0 120 120" className="h-24 w-24 text-ink-muted">
        <circle cx="60" cy="60" r="48" fill="#F3EEE7" stroke="currentColor" strokeWidth="3" />
        <path d="M60 34v34" stroke="currentColor" strokeWidth="6" strokeLinecap="round" />
        <circle cx="60" cy="84" r="5" fill="currentColor" />
      </svg>
      <p className="font-display text-display-md">열람이 제한된 페이지예요</p>
      <div className="text-body text-ink-muted">
        <p>보관주기(30일)가 지나 더 이상</p>
        <p>페이지를 볼 수 없어요</p>
      </div>
      <div className="text-body-sm text-ink-muted">
        <p>데이터는 삭제되지 않았어요 — 소유자가</p>
        <p>로그인하면 1회에 한해 30일 연장할 수 있어요</p>
      </div>
      <a
        href="/login"
        className="rounded-radius-full bg-accent-primary px-space-4 py-space-2 text-label text-surface-100"
      >
        소유자 로그인 (매직링크)
      </a>
    </Screen>
  );
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS (테스트 출력에 React `act` 경고가 없어야 한다)

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/components web/src/hooks
git commit -m "feat(web): 공통 레이아웃/버튼, 타이핑 훅, 인트로·비밀·만료 화면 추가"
```

---

### Task 5: 선택 화면 + 결과 리빌 화면(3개 테마)

**Files:**
- Create: `gender-reveal/web/src/lib/result.ts`, `result.test.ts`
- Create: `gender-reveal/web/src/components/PreReveal.tsx`
- Create: `gender-reveal/web/src/components/screens/SelectScreen.tsx`, `ResultScreen.tsx`
- Create: `gender-reveal/web/src/components/screens/SelectScreen.test.tsx`, `ResultScreen.test.tsx`

**Interfaces:**
- Consumes: `revealSrc`/`babySrc`(Task 2), `zodiacFromDueDate`/`zodiacLabelKo`(Task 2), `OpenPageView`/`Gender`/`Theme`(Task 3), `Button`/`Screen`/`usePrefersReducedMotion`(Task 4)
- Produces: `genderKo(g): '남아'|'여아'`, `revealHeadline(g): string`, `guessSummary(guess, actual): string`, `REVEAL_PROMPT: Record<Theme,string>`, `<PreReveal theme />`, `<SelectScreen onSelect submitting error />`, `<ResultScreen page guess onNext revealDelayMs? />`

**카피(그대로 사용):**
- 선택: 제목 `당신의 예상은?`, 버튼 `남자 아기`/`여자 아기`, 하단 `한 번만 참여할 수 있어요 (쿠키 기준, 로그인 불필요)`
- 리빌 전 안내: box `선물상자를 열어보세요`, cake `케이크를 잘라보세요`, balloon `풍선을 터뜨려보세요` (버튼 이름으로도 쓴다)
- 결과: `축하합니다` / 남아 `왕자님이 찾아왔어요!`, 여아 `공주님이 찾아왔어요!` / 띠가 있으면 `{말띠}둥이가 찾아왔어요!` / `내 예측: 여아 → 결과: 오답`(또는 `정답`) / 버튼 `다음: 축하글 남기기 ▶`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/lib/result.test.ts`:
```ts
import { genderKo, guessSummary, revealHeadline } from './result';

describe('result copy', () => {
  it('names the genders in Korean', () => {
    expect(genderKo('boy')).toBe('남아');
    expect(genderKo('girl')).toBe('여아');
  });

  it('announces the prince or princess', () => {
    expect(revealHeadline('boy')).toBe('왕자님이 찾아왔어요!');
    expect(revealHeadline('girl')).toBe('공주님이 찾아왔어요!');
  });

  it('summarises whether the guess was right', () => {
    expect(guessSummary('girl', 'boy')).toBe('내 예측: 여아 → 결과: 오답');
    expect(guessSummary('boy', 'boy')).toBe('내 예측: 남아 → 결과: 정답');
  });
});
```

`gender-reveal/web/src/components/screens/SelectScreen.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SelectScreen } from './SelectScreen';

describe('SelectScreen', () => {
  it('reports the chosen gender', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<SelectScreen onSelect={onSelect} submitting={false} error={null} />);

    expect(screen.getByText('당신의 예상은?')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '남자 아기' }));
    await user.click(screen.getByRole('button', { name: '여자 아기' }));

    expect(onSelect).toHaveBeenNthCalledWith(1, 'boy');
    expect(onSelect).toHaveBeenNthCalledWith(2, 'girl');
    expect(screen.getByText('한 번만 참여할 수 있어요 (쿠키 기준, 로그인 불필요)')).toBeInTheDocument();
  });

  it('disables both choices while submitting', () => {
    render(<SelectScreen onSelect={() => {}} submitting error={null} />);

    expect(screen.getByRole('button', { name: '남자 아기' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '여자 아기' })).toBeDisabled();
  });

  it('shows an error message as an alert', () => {
    render(<SelectScreen onSelect={() => {}} submitting={false} error="잠시 후 다시 시도해 주세요" />);

    expect(screen.getByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
  });
});
```

`gender-reveal/web/src/components/screens/ResultScreen.test.tsx`:
```tsx
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResultScreen } from './ResultScreen';
import type { OpenPageView } from '@/lib/types';

const basePage: OpenPageView = {
  status: 'open', nickname: '뽀튼이', actualGender: 'boy', dueDate: null, message: null, theme: 'box',
};

beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
afterEach(() => vi.useRealTimers());

async function reveal(theme: string) {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  await user.click(screen.getByRole('button', { name: theme }));
  act(() => { vi.advanceTimersByTime(1000); });
}

describe('ResultScreen', () => {
  it('hides the result until the guest opens the box', async () => {
    render(<ResultScreen page={basePage} guess="girl" onNext={() => {}} />);

    expect(screen.queryByText('왕자님이 찾아왔어요!')).not.toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /남아/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '선물상자를 열어보세요' })).toBeInTheDocument();

    await reveal('선물상자를 열어보세요');

    expect(screen.getByText('축하합니다')).toBeInTheDocument();
    expect(screen.getByText('왕자님이 찾아왔어요!')).toBeInTheDocument();
    expect(screen.getByText('내 예측: 여아 → 결과: 오답')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '젠더 리빌 결과: 남아' })).toHaveAttribute(
      'src', '/illustrations/reveal/box-boy.svg',
    );
  });

  it('uses the cake scene and princess copy for a girl', async () => {
    render(<ResultScreen page={{ ...basePage, actualGender: 'girl', theme: 'cake' }} guess="girl" onNext={() => {}} />);

    await reveal('케이크를 잘라보세요');

    expect(screen.getByText('공주님이 찾아왔어요!')).toBeInTheDocument();
    expect(screen.getByText('내 예측: 여아 → 결과: 정답')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '젠더 리빌 결과: 여아' })).toHaveAttribute(
      'src', '/illustrations/reveal/cake-girl.svg',
    );
  });

  it('uses the balloon scene', async () => {
    render(<ResultScreen page={{ ...basePage, theme: 'balloon' }} guess="boy" onNext={() => {}} />);

    await reveal('풍선을 터뜨려보세요');

    expect(screen.getByRole('img', { name: '젠더 리빌 결과: 남아' })).toHaveAttribute(
      'src', '/illustrations/reveal/balloon-boy.svg',
    );
  });

  it('shows the zodiac line and the owner message when present', async () => {
    render(
      <ResultScreen
        page={{ ...basePage, dueDate: '2026-11-03', message: '건강하게 만나요' }}
        guess="boy"
        onNext={() => {}}
      />,
    );

    await reveal('선물상자를 열어보세요');

    expect(screen.getByText('말띠둥이가 찾아왔어요!')).toBeInTheDocument();
    expect(screen.getByText('건강하게 만나요')).toBeInTheDocument();
  });

  it('omits the zodiac line without a due date', async () => {
    render(<ResultScreen page={basePage} guess="boy" onNext={() => {}} />);

    await reveal('선물상자를 열어보세요');

    expect(screen.queryByText(/둥이가 찾아왔어요/)).not.toBeInTheDocument();
  });

  it('offers the guestbook only after the reveal', async () => {
    const onNext = vi.fn();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ResultScreen page={basePage} guess="boy" onNext={onNext} />);

    expect(screen.queryByRole('button', { name: '다음: 축하글 남기기 ▶' })).not.toBeInTheDocument();
    await reveal('선물상자를 열어보세요');
    await user.click(screen.getByRole('button', { name: '다음: 축하글 남기기 ▶' }));

    expect(onNext).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/lib/result.ts`:
```ts
import type { Gender, Theme } from './types';

export const genderKo = (gender: Gender) => (gender === 'boy' ? '남아' : '여아');

export const revealHeadline = (gender: Gender) =>
  gender === 'boy' ? '왕자님이 찾아왔어요!' : '공주님이 찾아왔어요!';

export const guessSummary = (guess: Gender, actual: Gender) =>
  `내 예측: ${genderKo(guess)} → 결과: ${guess === actual ? '정답' : '오답'}`;

export const REVEAL_PROMPT: Record<Theme, string> = {
  box: '선물상자를 열어보세요',
  cake: '케이크를 잘라보세요',
  balloon: '풍선을 터뜨려보세요',
};
```

`gender-reveal/web/src/components/PreReveal.tsx` (리빌 전 중립 그림 — 성별을 암시하지 않도록 무채색):
```tsx
import type { Theme } from '@/lib/types';

const STROKE = '#8C8579';
const FILL = '#F3EEE7';
const WHITE = '#FFFFFF';

export function PreReveal({ theme }: { theme: Theme }) {
  return (
    <svg aria-hidden viewBox="0 0 200 200" className="h-48 w-48">
      {theme === 'box' && (
        <g stroke={STROKE} strokeWidth="3" strokeLinejoin="round">
          <rect x="40" y="90" width="120" height="80" rx="8" fill={FILL} />
          <rect x="32" y="66" width="136" height="28" rx="8" fill={WHITE} />
          <path d="M100 66v104" fill="none" />
          <path d="M100 66c-20-30-48-12-30 0M100 66c20-30 48-12 30 0" fill="none" />
        </g>
      )}
      {theme === 'cake' && (
        <g stroke={STROKE} strokeWidth="3" strokeLinejoin="round">
          <rect x="30" y="110" width="140" height="50" rx="10" fill={FILL} />
          <rect x="44" y="76" width="112" height="38" rx="10" fill={WHITE} />
          <path d="M100 76V56" fill="none" />
          <path d="M100 56c-6-8 0-14 0-14s6 6 0 14z" fill={FILL} />
        </g>
      )}
      {theme === 'balloon' && (
        <g stroke={STROKE} strokeWidth="3" strokeLinejoin="round">
          <ellipse cx="100" cy="88" rx="52" ry="62" fill={FILL} />
          <path d="M92 150l8 10 8-10z" fill={FILL} />
          <path d="M100 160c-10 14 10 20 0 34" fill="none" />
        </g>
      )}
    </svg>
  );
}
```

`gender-reveal/web/src/components/screens/SelectScreen.tsx`:
```tsx
import { Screen } from '../Screen';
import { babySrc } from '@/lib/illustrations';
import type { Gender } from '@/lib/types';

const CHOICES: { gender: Gender; label: string }[] = [
  { gender: 'boy', label: '남자 아기' },
  { gender: 'girl', label: '여자 아기' },
];

// Neutral on purpose: this screen appears before the reveal, so no gender accent colors here.
export function SelectScreen({
  onSelect,
  submitting,
  error,
}: {
  onSelect: (gender: Gender) => void;
  submitting: boolean;
  error: string | null;
}) {
  return (
    <Screen>
      <h1 className="font-display text-display-lg">당신의 예상은?</h1>
      <div className="flex w-full gap-space-3">
        {CHOICES.map(({ gender, label }) => (
          <button
            key={gender}
            type="button"
            disabled={submitting}
            onClick={() => onSelect(gender)}
            className="flex flex-1 flex-col items-center gap-space-2 rounded-radius-md border border-border bg-surface-100 p-space-3 transition hover:border-accent-primary disabled:cursor-not-allowed disabled:opacity-50"
          >
            <img src={babySrc(gender)} alt="" className="h-28 w-28" />
            <span className="text-body-lg">{label}</span>
          </button>
        ))}
      </div>
      {error && (
        <p role="alert" className="text-body text-danger">
          {error}
        </p>
      )}
      <p className="text-body-sm text-ink-muted">한 번만 참여할 수 있어요 (쿠키 기준, 로그인 불필요)</p>
    </Screen>
  );
}
```
(각 버튼의 접근 가능한 이름은 이미지 `alt=""` + 라벨 텍스트이므로 `남자 아기`/`여자 아기`가 된다.)

`gender-reveal/web/src/components/screens/ResultScreen.tsx`:
```tsx
'use client';

import { useEffect, useRef, useState } from 'react';
import { Button } from '../Button';
import { PreReveal } from '../PreReveal';
import { Screen } from '../Screen';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import { revealSrc } from '@/lib/illustrations';
import { genderKo, guessSummary, REVEAL_PROMPT, revealHeadline } from '@/lib/result';
import type { Gender, OpenPageView } from '@/lib/types';
import { zodiacFromDueDate, zodiacLabelKo } from '@/lib/zodiac';

type Stage = 'ready' | 'revealing' | 'revealed';

const HEARTS = [10, 24, 38, 52, 66, 80];

export function ResultScreen({
  page,
  guess,
  onNext,
  revealDelayMs = 900,
}: {
  page: OpenPageView;
  guess: Gender;
  onNext: () => void;
  revealDelayMs?: number;
}) {
  const reduced = usePrefersReducedMotion();
  const [stage, setStage] = useState<Stage>('ready');
  const timer = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => () => clearTimeout(timer.current), []);

  const startReveal = () => {
    if (stage !== 'ready') return;
    if (reduced) {
      setStage('revealed');
      return;
    }
    setStage('revealing');
    timer.current = setTimeout(() => setStage('revealed'), revealDelayMs);
  };

  if (stage !== 'revealed') {
    return (
      <Screen>
        <button
          type="button"
          onClick={startReveal}
          disabled={stage === 'revealing'}
          className={`flex flex-col items-center gap-space-3 ${stage === 'revealing' ? 'animate-shake' : ''}`}
        >
          <PreReveal theme={page.theme} />
          <span className="rounded-radius-full bg-accent-primary px-space-4 py-space-2 text-label text-surface-100">
            {REVEAL_PROMPT[page.theme]}
          </span>
        </button>
      </Screen>
    );
  }

  const accent = page.actualGender === 'boy' ? 'text-accent-boy' : 'text-accent-girl';
  const soft = page.actualGender === 'boy' ? 'bg-accent-boy-soft' : 'bg-accent-girl-soft';
  const zodiac = zodiacFromDueDate(page.dueDate);

  return (
    <Screen className={soft}>
      <div aria-hidden className="pointer-events-none absolute inset-0">
        {HEARTS.map((left, i) => (
          <span
            key={left}
            className={`absolute bottom-16 ${accent} opacity-0 motion-safe:animate-float-up`}
            style={{ left: `${left}%`, animationDelay: `${i * 0.5}s` }}
          >
            ♥
          </span>
        ))}
      </div>
      <img
        src={revealSrc(page.theme, page.actualGender)}
        alt={`젠더 리빌 결과: ${genderKo(page.actualGender)}`}
        className="h-56 w-56 motion-safe:animate-pop-in"
      />
      <p className={`font-display text-display-md ${accent}`}>축하합니다</p>
      <p className="font-display text-display-lg">{revealHeadline(page.actualGender)}</p>
      {zodiac && <p className="text-body-lg">{zodiacLabelKo(zodiac).replace('띠', '띠둥이')}가 찾아왔어요!</p>}
      {page.message && <p className="text-body text-ink-muted">{page.message}</p>}
      <p className="rounded-radius-full bg-surface-100 px-space-3 py-space-1 text-label">
        {guessSummary(guess, page.actualGender)}
      </p>
      <Button onClick={onNext}>다음: 축하글 남기기 ▶</Button>
    </Screen>
  );
}
```
주의: 띠 문구는 `말띠둥이가 찾아왔어요!` 형태다. 위 `replace('띠', '띠둥이')` 대신 읽기 쉽게 `` `${zodiacLabelKo(zodiac)}둥이가 찾아왔어요!` ``로 써도 된다(결과 동일). 구현자는 가독성이 좋은 쪽을 택한다.

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS (act 경고 없음)

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/lib/result.ts web/src/lib/result.test.ts web/src/components
git commit -m "feat(web): 성별 선택 화면과 3개 테마 결과 리빌 화면 추가"
```

---

### Task 6: 축하글 화면

**Files:**
- Create: `gender-reveal/web/src/components/screens/GuestbookScreen.tsx`
- Create: `gender-reveal/web/src/components/screens/GuestbookScreen.test.tsx`

**Interfaces:**
- Consumes: `getGuestbook`/`postGuestbook`/`ApiError`(Task 3), `relativeTime`(Task 3), `Screen`/`Button`(Task 4)
- Produces: `<GuestbookScreen slug nowMs? />` — 마운트 시 목록 조회, 작성 폼(닉네임 ≤40, 메시지 ≤500), 등록 후 목록 새로고침

**카피(그대로 사용):** 제목 `축하 메시지 남기기`, 라벨 `닉네임`/`메시지`, 버튼 `등록`, 목록 제목 `등록된 메시지`, 항목 `{닉네임} · {방금 전|N분 전|N시간 전|N일 전}`, 하단 `닉네임으로만 구분되며 여러 번 남길 수 있어요 · 부적절한 글은 관리자가 숨길 수 있어요`, 빈 목록 `아직 등록된 메시지가 없어요`, 오류 `닉네임과 메시지를 입력해 주세요`(클라이언트 검증·400) / `잠시 후 다시 시도해 주세요`(그 외)

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/components/screens/GuestbookScreen.test.tsx`:
```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GuestbookScreen } from './GuestbookScreen';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getGuestbook: vi.fn(), postGuestbook: vi.fn() };
});

const getGuestbook = vi.mocked(api.getGuestbook);
const postGuestbook = vi.mocked(api.postGuestbook);

const NOW = Date.parse('2026-09-21T12:00:00.000Z');
const entry = (nickname: string, message: string, msAgo: number) => ({
  nickname, message, createdAt: new Date(NOW - msAgo).toISOString(),
});

beforeEach(() => {
  getGuestbook.mockReset();
  postGuestbook.mockReset();
});

describe('GuestbookScreen', () => {
  it('lists existing messages with relative times', async () => {
    getGuestbook.mockResolvedValue([entry('이모', '축하해요!', 10_000), entry('삼촌', '고생 많으셨어요', 3 * 3_600_000)]);

    render(<GuestbookScreen slug="s" nowMs={NOW} />);

    expect(await screen.findByText('이모 · 방금 전')).toBeInTheDocument();
    expect(screen.getByText('축하해요!')).toBeInTheDocument();
    expect(screen.getByText('삼촌 · 3시간 전')).toBeInTheDocument();
    expect(getGuestbook).toHaveBeenCalledWith('s');
  });

  it('shows an empty state', async () => {
    getGuestbook.mockResolvedValue([]);

    render(<GuestbookScreen slug="s" nowMs={NOW} />);

    expect(await screen.findByText('아직 등록된 메시지가 없어요')).toBeInTheDocument();
  });

  it('renders message text literally, never as HTML', async () => {
    getGuestbook.mockResolvedValue([entry('<b>해커</b>', '<img src=x onerror=alert(1)>', 1000)]);

    const { container } = render(<GuestbookScreen slug="s" nowMs={NOW} />);

    expect(await screen.findByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(container.querySelector('img[src="x"]')).toBeNull();
    expect(container.querySelector('b')).toBeNull();
  });

  it('posts a trimmed entry, refreshes the list and clears the message', async () => {
    const user = userEvent.setup();
    getGuestbook.mockResolvedValueOnce([]);
    postGuestbook.mockResolvedValue(entry('이모', '축하해요', 0));
    getGuestbook.mockResolvedValueOnce([entry('이모', '축하해요', 0)]);
    render(<GuestbookScreen slug="s" nowMs={NOW} />);
    await screen.findByText('아직 등록된 메시지가 없어요');

    await user.type(screen.getByLabelText('닉네임'), '  이모 ');
    await user.type(screen.getByLabelText('메시지'), ' 축하해요 ');
    await user.click(screen.getByRole('button', { name: '등록' }));

    await waitFor(() => expect(postGuestbook).toHaveBeenCalledWith('s', { nickname: '이모', message: '축하해요' }));
    expect(await screen.findByText('이모 · 방금 전')).toBeInTheDocument();
    expect(screen.getByLabelText('메시지')).toHaveValue('');
    expect(screen.getByLabelText('닉네임')).toHaveValue('  이모 ');
  });

  it('refuses to submit blank fields', async () => {
    const user = userEvent.setup();
    getGuestbook.mockResolvedValue([]);
    render(<GuestbookScreen slug="s" nowMs={NOW} />);
    await screen.findByText('아직 등록된 메시지가 없어요');

    await user.click(screen.getByRole('button', { name: '등록' }));

    expect(screen.getByRole('alert')).toHaveTextContent('닉네임과 메시지를 입력해 주세요');
    expect(postGuestbook).not.toHaveBeenCalled();
  });

  it('shows a generic error when posting fails', async () => {
    const user = userEvent.setup();
    getGuestbook.mockResolvedValue([]);
    postGuestbook.mockRejectedValue(new api.ApiError(500, null));
    render(<GuestbookScreen slug="s" nowMs={NOW} />);
    await screen.findByText('아직 등록된 메시지가 없어요');

    await user.type(screen.getByLabelText('닉네임'), '이모');
    await user.type(screen.getByLabelText('메시지'), '축하해요');
    await user.click(screen.getByRole('button', { name: '등록' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
  });

  it('shows a load error without crashing', async () => {
    getGuestbook.mockRejectedValue(new api.ApiError(500, null));

    render(<GuestbookScreen slug="s" nowMs={NOW} />);

    expect(await screen.findByText('메시지를 불러오지 못했어요')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/components/screens/GuestbookScreen.tsx`:
```tsx
'use client';

import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '../Button';
import { getGuestbook, postGuestbook } from '@/lib/api';
import { ApiError } from '@/lib/api';
import { relativeTime } from '@/lib/time';
import type { GuestbookEntry } from '@/lib/types';

const inputClass =
  'w-full rounded-radius-sm border border-border bg-surface-100 px-space-2 py-space-2 text-body outline-none focus:border-accent-primary';

export function GuestbookScreen({ slug, nowMs }: { slug: string; nowMs?: number }) {
  const [entries, setEntries] = useState<GuestbookEntry[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [nickname, setNickname] = useState('');
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setEntries(await getGuestbook(slug));
      setLoadFailed(false);
    } catch {
      setLoadFailed(true);
    }
  }, [slug]);

  useEffect(() => {
    void load();
  }, [load]);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const trimmedNickname = nickname.trim();
    const trimmedMessage = message.trim();
    if (!trimmedNickname || !trimmedMessage) {
      setFormError('닉네임과 메시지를 입력해 주세요');
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      await postGuestbook(slug, { nickname: trimmedNickname, message: trimmedMessage });
      setMessage('');
      await load();
    } catch (error) {
      setFormError(
        error instanceof ApiError && error.status === 400
          ? '닉네임과 메시지를 입력해 주세요'
          : '잠시 후 다시 시도해 주세요',
      );
    } finally {
      setSubmitting(false);
    }
  };

  const now = nowMs ?? Date.now();

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[420px] flex-col gap-space-4 px-space-3 py-space-5">
      <h1 className="text-center font-display text-display-lg">축하 메시지 남기기</h1>

      <form onSubmit={onSubmit} className="flex flex-col gap-space-2 rounded-radius-md border border-border bg-surface-100 p-space-3">
        <label className="flex flex-col gap-space-1 text-label">
          닉네임
          <input className={inputClass} value={nickname} maxLength={40} onChange={(e) => setNickname(e.target.value)} />
        </label>
        <label className="flex flex-col gap-space-1 text-label">
          메시지
          <textarea className={inputClass} rows={3} value={message} maxLength={500} onChange={(e) => setMessage(e.target.value)} />
        </label>
        {formError && (
          <p role="alert" className="text-body-sm text-danger">
            {formError}
          </p>
        )}
        <Button type="submit" disabled={submitting}>
          등록
        </Button>
      </form>

      <section className="flex flex-col gap-space-2">
        <h2 className="font-display text-display-md">등록된 메시지</h2>
        {loadFailed && <p className="text-body text-ink-muted">메시지를 불러오지 못했어요</p>}
        {entries && entries.length === 0 && <p className="text-body text-ink-muted">아직 등록된 메시지가 없어요</p>}
        <ul className="flex flex-col gap-space-2">
          {entries?.map((entry, i) => (
            <li key={`${entry.createdAt}-${i}`} className="rounded-radius-md border border-border bg-surface-100 p-space-3">
              <p className="text-label text-ink-muted">
                {entry.nickname} · {relativeTime(entry.createdAt, now)}
              </p>
              <p className="mt-space-1 whitespace-pre-line break-words text-body">{entry.message}</p>
            </li>
          ))}
        </ul>
      </section>

      <p className="text-center text-body-sm text-ink-muted">
        닉네임으로만 구분되며 여러 번 남길 수 있어요 · 부적절한 글은 관리자가 숨길 수 있어요
      </p>
    </main>
  );
}
```
주의: `<Button>`은 `type="button"`이 기본이므로 `type="submit"`을 그대로 덮어쓰게 되어 있다(`{...props}`가 뒤에 있음). 동작을 테스트가 확인한다. 두 `import ... from '@/lib/api'`는 한 줄로 합쳐도 된다.

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS

- [ ] **Step 5: 커밋**

```bash
cd gender-reveal
git add web/src/components/screens/GuestbookScreen.tsx web/src/components/screens/GuestbookScreen.test.tsx
git commit -m "feat(web): 축하글 화면(목록·작성, 텍스트로만 렌더) 추가"
```

---

### Task 7: `/g` 페이지 상태 머신 (`GuestPage`)

**Files:**
- Create: `gender-reveal/web/src/components/GuestPage.tsx`
- Create: `gender-reveal/web/src/components/GuestPage.test.tsx`
- Create: `gender-reveal/web/src/app/g/page.tsx`

**Interfaces:**
- Consumes: 앞 태스크의 화면들, `getPage`/`submitGuess`/`ApiError`(Task 3), `zodiacFromDueDate`(Task 2)
- Produces: `slugFromPathname(pathname): string | null`, `<GuestPage />`(클라이언트 컴포넌트; `window.location.pathname`에서 슬러그를 읽음), 라우트 `/g`(정적 셸)

**상태 흐름:** 슬러그 없음/404 → 안내 `존재하지 않는 페이지예요`. 기타 오류 → `잠시 후 다시 시도해 주세요` + `다시 시도` 버튼. `secret` → SecretScreen. `expired` → ExpiredScreen. `open` → intro → select → (맞추기 POST) → result → guestbook. 맞추기 중 409(페이지가 open이 아니게 됨)이면 페이지를 다시 불러온다. 그 외 오류는 선택 화면에 `잠시 후 다시 시도해 주세요`를 보여준다.

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/components/GuestPage.test.tsx`:
```tsx
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GuestPage, slugFromPathname } from './GuestPage';
import * as api from '@/lib/api';
import type { PageView } from '@/lib/types';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, getPage: vi.fn(), submitGuess: vi.fn(), getGuestbook: vi.fn(), postGuestbook: vi.fn() };
});

const getPage = vi.mocked(api.getPage);
const submitGuess = vi.mocked(api.submitGuess);
const getGuestbook = vi.mocked(api.getGuestbook);

const openPage: PageView = {
  status: 'open', nickname: '뽀튼이', actualGender: 'boy', dueDate: '2026-11-03', message: null, theme: 'box',
};

beforeEach(() => {
  getPage.mockReset();
  submitGuess.mockReset();
  getGuestbook.mockReset();
  getGuestbook.mockResolvedValue([]);
  window.history.pushState({}, '', '/g/my-slug');
});

describe('slugFromPathname', () => {
  it('extracts the slug from /g/<slug>', () => {
    expect(slugFromPathname('/g/my-slug')).toBe('my-slug');
    expect(slugFromPathname('/g/my-slug/')).toBe('my-slug');
  });

  it('returns null for other paths', () => {
    expect(slugFromPathname('/g')).toBeNull();
    expect(slugFromPathname('/g/')).toBeNull();
    expect(slugFromPathname('/x/y')).toBeNull();
    expect(slugFromPathname('/g/a/b')).toBeNull();
  });
});

describe('GuestPage', () => {
  it('loads the page by the slug in the URL', async () => {
    getPage.mockResolvedValue({ status: 'secret', nickname: '뽀튼이' });

    render(<GuestPage />);

    expect(await screen.findByText('Coming Soon')).toBeInTheDocument();
    expect(getPage).toHaveBeenCalledWith('my-slug');
  });

  it('shows the expired screen', async () => {
    getPage.mockResolvedValue({ status: 'expired', nickname: '뽀튼이' });

    render(<GuestPage />);

    expect(await screen.findByText('열람이 제한된 페이지예요')).toBeInTheDocument();
  });

  it('tells the visitor when the page does not exist', async () => {
    getPage.mockRejectedValue(new api.ApiError(404, { error: 'Page not found' }));

    render(<GuestPage />);

    expect(await screen.findByText('존재하지 않는 페이지예요')).toBeInTheDocument();
  });

  it('offers a retry after a server error', async () => {
    const user = userEvent.setup();
    getPage.mockRejectedValueOnce(new api.ApiError(500, null));
    getPage.mockResolvedValueOnce({ status: 'secret', nickname: '뽀튼이' });

    render(<GuestPage />);
    await screen.findByText('잠시 후 다시 시도해 주세요');
    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    expect(await screen.findByText('Coming Soon')).toBeInTheDocument();
  });

  it('walks intro → select → result → guestbook', async () => {
    const user = userEvent.setup();
    getPage.mockResolvedValue(openPage);
    submitGuess.mockResolvedValue({ guessedGender: 'girl', alreadyGuessed: false });

    render(<GuestPage />);

    await user.click(await screen.findByRole('button', { name: '탭해서 계속하기' }));
    await user.click(await screen.findByRole('button', { name: '여자 아기' }));
    expect(submitGuess).toHaveBeenCalledWith('my-slug', 'girl');

    await user.click(await screen.findByRole('button', { name: '선물상자를 열어보세요' }));
    expect(await screen.findByText('왕자님이 찾아왔어요!', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByText('내 예측: 여아 → 결과: 오답')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다음: 축하글 남기기 ▶' }));
    expect(await screen.findByText('축하 메시지 남기기')).toBeInTheDocument();
    expect(getGuestbook).toHaveBeenCalledWith('my-slug');
  });

  it('uses the earlier guess when the guest already participated', async () => {
    const user = userEvent.setup();
    getPage.mockResolvedValue(openPage);
    submitGuess.mockResolvedValue({ guessedGender: 'boy', alreadyGuessed: true });

    render(<GuestPage />);
    await user.click(await screen.findByRole('button', { name: '탭해서 계속하기' }));
    await user.click(await screen.findByRole('button', { name: '여자 아기' }));
    await user.click(await screen.findByRole('button', { name: '선물상자를 열어보세요' }));

    expect(await screen.findByText('내 예측: 남아 → 결과: 정답', {}, { timeout: 3000 })).toBeInTheDocument();
  });

  it('shows an error on the select screen when guessing fails', async () => {
    const user = userEvent.setup();
    getPage.mockResolvedValue(openPage);
    submitGuess.mockRejectedValue(new api.ApiError(500, null));

    render(<GuestPage />);
    await user.click(await screen.findByRole('button', { name: '탭해서 계속하기' }));
    await user.click(await screen.findByRole('button', { name: '여자 아기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
    expect(screen.getByRole('button', { name: '여자 아기' })).toBeEnabled();
  });

  it('reloads the page when the guess is rejected because the page is no longer open', async () => {
    const user = userEvent.setup();
    getPage.mockResolvedValueOnce(openPage);
    getPage.mockResolvedValueOnce({ status: 'expired', nickname: '뽀튼이' });
    submitGuess.mockRejectedValue(new api.ApiError(409, { error: 'Page is not open' }));

    render(<GuestPage />);
    await user.click(await screen.findByRole('button', { name: '탭해서 계속하기' }));
    await user.click(await screen.findByRole('button', { name: '여자 아기' }));

    expect(await screen.findByText('열람이 제한된 페이지예요')).toBeInTheDocument();
    await waitFor(() => expect(getPage).toHaveBeenCalledTimes(2));
  });
});
```
(`act` import가 쓰이지 않으면 제거한다.)

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: 모듈 없음으로 실패

- [ ] **Step 3: 구현**

`gender-reveal/web/src/components/GuestPage.tsx`:
```tsx
'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button } from './Button';
import { Screen } from './Screen';
import { ExpiredScreen } from './screens/ExpiredScreen';
import { GuestbookScreen } from './screens/GuestbookScreen';
import { IntroScreen } from './screens/IntroScreen';
import { ResultScreen } from './screens/ResultScreen';
import { SecretScreen } from './screens/SecretScreen';
import { SelectScreen } from './screens/SelectScreen';
import { ApiError, getPage, submitGuess } from '@/lib/api';
import type { Gender, PageView } from '@/lib/types';
import { zodiacFromDueDate } from '@/lib/zodiac';

type Load = { kind: 'loading' } | { kind: 'notfound' } | { kind: 'error' } | { kind: 'ready'; view: PageView };
type Stage = 'intro' | 'select' | 'result' | 'guestbook';

/** The static shell is served for every /g/<slug>; the slug only exists in the browser URL. */
export function slugFromPathname(pathname: string): string | null {
  const match = /^\/g\/([^/]+)\/?$/.exec(pathname);
  return match ? decodeURIComponent(match[1]) : null;
}

export function GuestPage() {
  const [slug, setSlug] = useState<string | null | undefined>(undefined);
  const [load, setLoad] = useState<Load>({ kind: 'loading' });
  const [stage, setStage] = useState<Stage>('intro');
  const [guess, setGuess] = useState<Gender | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [selectError, setSelectError] = useState<string | null>(null);

  useEffect(() => {
    setSlug(slugFromPathname(window.location.pathname));
  }, []);

  const loadPage = useCallback(async (currentSlug: string) => {
    setLoad({ kind: 'loading' });
    try {
      setLoad({ kind: 'ready', view: await getPage(currentSlug) });
    } catch (error) {
      setLoad(error instanceof ApiError && error.status === 404 ? { kind: 'notfound' } : { kind: 'error' });
    }
  }, []);

  useEffect(() => {
    if (slug) void loadPage(slug);
  }, [slug, loadPage]);

  const goToSelect = useCallback(() => setStage('select'), []);

  const onSelect = async (gender: Gender) => {
    if (!slug) return;
    setSubmitting(true);
    setSelectError(null);
    try {
      const result = await submitGuess(slug, gender);
      setGuess(result.guessedGender);
      setStage('result');
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setStage('intro');
        await loadPage(slug); // the page is no longer open (e.g. it expired meanwhile)
      } else {
        setSelectError('잠시 후 다시 시도해 주세요');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (slug === undefined || load.kind === 'loading') {
    return slug === null ? <NotFound /> : <Screen><p className="text-body text-ink-muted">불러오는 중…</p></Screen>;
  }
  if (slug === null || load.kind === 'notfound') return <NotFound />;
  if (load.kind === 'error') {
    return (
      <Screen>
        <p className="text-body">잠시 후 다시 시도해 주세요</p>
        <Button onClick={() => void loadPage(slug)}>다시 시도</Button>
      </Screen>
    );
  }

  const { view } = load;
  if (view.status === 'secret') return <SecretScreen nickname={view.nickname} />;
  if (view.status === 'expired') return <ExpiredScreen />;

  if (stage === 'intro') {
    return <IntroScreen nickname={view.nickname} zodiac={zodiacFromDueDate(view.dueDate)} onNext={goToSelect} />;
  }
  if (stage === 'select' || !guess) {
    return <SelectScreen onSelect={onSelect} submitting={submitting} error={selectError} />;
  }
  if (stage === 'result') {
    return <ResultScreen page={view} guess={guess} onNext={() => setStage('guestbook')} />;
  }
  return <GuestbookScreen slug={slug} />;
}

function NotFound() {
  return (
    <Screen>
      <p className="font-display text-display-md">존재하지 않는 페이지예요</p>
      <p className="text-body text-ink-muted">주소를 다시 확인해 주세요</p>
    </Screen>
  );
}
```

`gender-reveal/web/src/app/g/page.tsx`:
```tsx
import type { Metadata } from 'next';
import { GuestPage } from '@/components/GuestPage';

// Guest pages are private-by-link; keep them out of search indexes.
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function Page() {
  return <GuestPage />;
}
```

- [ ] **Step 4: 통과 확인**

실행: `cd gender-reveal/web && npm test`
예상: 전체 PASS. 타이머/`act` 경고가 나오면 테스트가 아닌 구현의 상태 갱신 시점을 점검한다.

- [ ] **Step 5: 빌드 확인**

실행: `cd gender-reveal/web && npm run build`
예상: 성공. `out/g.html`과 `out/index.html`이 존재한다(`test -f out/g.html`).

- [ ] **Step 6: 커밋**

```bash
cd gender-reveal
git add web/src/components/GuestPage.tsx web/src/components/GuestPage.test.tsx web/src/app/g
git commit -m "feat(web): /g 방문자 페이지 상태 머신(인트로→선택→결과→축하글) 추가"
```

---

### Task 8: nginx 설정, 공통 OG 이미지, 실행 안내

**Files:**
- Create: `gender-reveal/web/nginx/gender-reveal.conf`
- Create: `gender-reveal/web/scripts/make-og.mjs`
- Create: `gender-reveal/web/public/og.png` (생성물, 커밋)
- Create: `gender-reveal/web/src/lib/og.test.ts`
- Create: `gender-reveal/web/README.md`

**Interfaces:**
- Consumes: `public/illustrations/baby/{boy,girl}.svg`(Task 2), `npm run og`
- Produces: nginx 서버 블록(`/api/` → Spring 프록시, `/g/<slug>` → `g.html`, 정적 자산 캐시), 1200×630 `public/og.png`

- [ ] **Step 1: 실패하는 테스트 작성**

`gender-reveal/web/src/lib/og.test.ts`:
```ts
import fs from 'node:fs';
import path from 'node:path';

describe('open graph image', () => {
  it('is a 1200x630 PNG', () => {
    const file = path.resolve(__dirname, '../../public/og.png');
    const bytes = fs.readFileSync(file);

    expect(bytes.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a');
    expect(bytes.readUInt32BE(16)).toBe(1200);
    expect(bytes.readUInt32BE(20)).toBe(630);
  });
});
```

- [ ] **Step 2: 실패 확인**

실행: `cd gender-reveal/web && npm test`
예상: `og.png` 없음으로 실패

- [ ] **Step 3: OG 이미지 생성 스크립트**

`gender-reveal/web/scripts/make-og.mjs` — 글자 없이 배경 + 아기 일러스트 두 장만 합성한다(서버에 한글 폰트가 없어도 깨지지 않게. 제목은 `og:title` 메타가 맡는다):
```js
import sharp from 'sharp';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const WIDTH = 1200;
const HEIGHT = 630;
const ART_HEIGHT = 420;

async function art(file) {
  const buffer = await sharp(path.join(root, 'public/illustrations/baby', file), { density: 300 })
    .resize({ height: ART_HEIGHT })
    .png()
    .toBuffer();
  const { width } = await sharp(buffer).metadata();
  return { buffer, width };
}

const boy = await art('boy.svg');
const girl = await art('girl.svg');
const gap = 80;
const totalWidth = boy.width + gap + girl.width;
const left = Math.round((WIDTH - totalWidth) / 2);
const top = Math.round((HEIGHT - ART_HEIGHT) / 2);

await sharp({ create: { width: WIDTH, height: HEIGHT, channels: 4, background: '#FFFBF6' } })
  .composite([
    { input: boy.buffer, left, top },
    { input: girl.buffer, left: left + boy.width + gap, top },
  ])
  .png()
  .toFile(path.join(root, 'public/og.png'));

console.log('wrote public/og.png');
```

- [ ] **Step 4: 생성 + 통과 확인**

실행: `cd gender-reveal/web && npm run og && npm test`
예상: `wrote public/og.png`, 전체 PASS. 이미지를 Read 도구로 열어 두 아기 일러스트가 잘리지 않고 가운데 배치돼 있는지 눈으로 확인한다.

- [ ] **Step 5: nginx 설정 작성**

`gender-reveal/web/nginx/gender-reveal.conf`:
```nginx
# Serves the static export (`npm run build` → web/out) and proxies /api to Spring.
# Mount web/out at /usr/share/nginx/html. In docker-compose the API service is `api`;
# for a bare-metal setup change the upstream to 127.0.0.1:8080.
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # Same-origin API: cookies (guest_id, owner_session) work without CORS.
    location /api/ {
        proxy_pass http://api:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Every /g/<slug> gets the same static shell; the slug is read in the browser.
    location ~ ^/g/[^/]+/?$ {
        add_header Cache-Control "no-store";
        try_files /g.html =404;
    }

    location /_next/static/ {
        add_header Cache-Control "public, max-age=31536000, immutable";
        try_files $uri =404;
    }

    location / {
        try_files $uri $uri.html $uri/ =404;
    }

    # TODO(Plan 5): /dashboard/<slug> needs the same shell treatment as /g/<slug>
    # once the dashboard page exists.
}
```
이 환경에는 nginx가 없어 `nginx -t`를 실행하지 못한다 — 보고서에 "문법 미검증"으로 명시한다.

- [ ] **Step 6: README 작성**

`gender-reveal/web/README.md`:
````markdown
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
npm run build     # 정적 파일 → out/
NEXT_PUBLIC_SITE_URL=https://example.com npm run build   # 배포 시 OG 이미지 절대 URL
npm run og        # public/og.png 재생성 (일러스트 변경 시)
```

## 배포

`out/`을 nginx 루트(`/usr/share/nginx/html`)에 두고 `nginx/gender-reveal.conf`를 사용한다.
공유 카드는 모든 링크가 동일한 제목·이미지다(정적 export 제약, 성별/공개 여부 비노출).
````

- [ ] **Step 7: 전체 확인**

실행: `cd gender-reveal/web && npm test && npm run build && test -f out/g.html && test -f out/index.html && test -f out/og.png`
예상: 모두 성공.

수동 확인(가능하면): API(`./gradlew bootRun`)와 `npm run dev`를 띄우고 Browser 패널에서 `/g/<존재하지 않는 슬러그>`가 "존재하지 않는 페이지예요"를 보여주는지 확인한다. 존재하는 페이지 확인은 로그인 UI가 Plan 5에 있으므로, API로 만든 페이지가 있을 때만 한다(없으면 생략하고 보고서에 적는다).

- [ ] **Step 8: 커밋**

```bash
cd gender-reveal
git add web/nginx web/scripts web/public/og.png web/src/lib/og.test.ts web/README.md
git commit -m "feat(web): nginx 설정, 공통 OG 이미지, 실행 안내 추가"
```

---

## 이 계획 완료 후 상태

- `web/`에서 `npm test`, `npm run build`가 통과하고, `next dev`(+ API)로 `/g/<slug>`의 비밀/만료/인트로/선택/결과(3테마)/축하글 흐름이 동작한다.
- 여전히 없는 것: 소유자 로그인(`/login`), 내 페이지 목록·대시보드(`/dashboard`, `/dashboard/[slug]`), 페이지 작성 폼(`/create`)과 미리보기 — **Plan 5**. nginx 설정의 `nginx -t` 검증과 docker-compose — Docker 태스크(Plan 1 Task 8).
- **Plan 5로 넘기는 것:** ① `/dashboard/<slug>`용 nginx 셸 규칙 추가, ② 만료 화면의 `/login` 링크와 매직링크 콜백 리다이렉트(`/dashboard`, `/login?error=invalid`) 대상 화면, ③ 로그인 후 `POST /api/pages` 생성 폼(`ownerEmail` 필드 없음 — 세션에서 결정됨), ④ 공유 링크 복사 UI, ⑤ 이 계획에서 미룬 항목(BGM·카운트다운·QR, 테마별 세부 리빌 연출, 맞춘 뒤에만 정답을 주는 API).

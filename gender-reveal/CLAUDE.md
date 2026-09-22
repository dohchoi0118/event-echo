# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This directory (`gender-reveal/`) is a subproject of the `event-echo` repo. Planning is done;
backend and the visitor-facing frontend are implemented (owner dashboard/create-form frontend not
started).

- **Backend — [api/](api/)**: Spring Boot 3.3 / Java 21 / Gradle (Kotlin DSL) / Spring Data JPA +
  Hibernate community SQLite dialect / Flyway / SQLite. Implemented: page creation + public
  view, guess (one per guest), guestbook, owner email magic-link auth + owner API.
- **Frontend — [web/](web/)**: Next.js 15 (App Router) with **static export** (`npm run build` →
  `out/`, no Node server) served by nginx, same-origin reverse proxy to the API
  (`web/nginx/gender-reveal.conf`). Implemented: the full visitor flow at `/g/<slug>` (one static
  shell for every slug, slug read client-side from `window.location.pathname`) — intro, secret,
  expired, guess selection, 3-theme result reveal, guestbook. `NEXT_PUBLIC_SITE_URL` is required for
  a production build (`npm run build`) — it fails fast otherwise, to avoid baking `localhost` into
  the OG share-card URL. Not yet built: owner-facing screens (`/login`, `/dashboard`, `/create` —
  Plan 5). BGM, countdown on the secret screen, and QR codes are **out of scope for the first
  release**.
- **Design/implementation docs — [docs/superpowers/](docs/superpowers/)**: the implementation design
  ([specs/](docs/superpowers/specs/2026-09-18-implementation-design.md), §11–13 record decisions
  from Plans 3–5) and step-by-step plans under [plans/](docs/superpowers/plans/). Plans: 1 backend
  foundation (done), 2 guess + guestbook API (done), 3 owner auth + admin API (done),
  4 Next.js frontend + visitor screens + nginx (done), 5 login + admin dashboard + create form
  (planned); the Docker / docker-compose task from Plan 1 is deferred until Docker is available.

All planning-stage documents (requirements, wireframe, work log) live under [planning/](planning/),
kept separate from the implementation source (`api/`, future `web/`).
It contains [planning/Requirements.md](planning/Requirements.md), a low-fidelity
[wireframe](planning/wireframe/README.md), a [design system](planning/design-system/README.md)
(color/typography/spacing/radius tokens, plus a 24-illustration asset library — 12 zodiac characters +
baby boy/girl + 6 reveal-theme graphics (surprise box/cake/balloon × boy/girl) + 4 generic props
(sneaker/car/dress/teddy bear), under `planning/design-system/project/assets/`. Style: soft gradient
fills + white "sticker" outline + soft ground shadow, matching the actual character art on the
reference site (see below) — this replaced an earlier bold-black-outline style once the reference
site's real character image was checked and found not to match it; the zodiac/baby silhouette shapes
were kept, only the paint treatment changed), and [high-fidelity screen mockups](planning/screens/README.md)
(9 screens — intro/select/result×3-themes/guestbook/secret/expired/admin — built on the design
system's tokens and illustration assets)
— all of these except Requirements.md are source copies of Claude Artifacts (Design Canvas /
Design System); see each file for its live link.

## What this subproject is

A "gender reveal" (젠더리빌) event page: an expectant parent creates a shareable page where guests
guess the baby's gender before the reveal.

Per [planning/Requirements.md](planning/Requirements.md), the planned feature set is:

- Page creation (페이지 작성) — nickname, real gender (kept private), scheduled reveal time, optional due date
  (used to auto-compute the Korean zodiac year/띠, shown with a matching zodiac-animal character illustration
  on both the intro and result screens — a 12-illustration asset set), message customization, choice of 3 reveal
  themes (surprise box / cake / balloon), fixed non-customizable BGM, and a pre-publish preview
- Shareable web link generation (웹주소 생성) — per-page unique URL, with an owner-settable custom slug
- Sharing (공유) — link copy + SNS share via OG preview card (no Kakao API integration; owner shares the link
  manually); the published link is auto-emailed to the *owner* only (not a guest-invite feature)
- Results dashboard for the page owner/admin (결과 대시보드) — magic-link email login (passwordless), visitor/guess
  stats, guestbook moderation, and extending an expired page (one-time 30-day extension only)
- Retention/expiry period for pages (보관주기) — auto-expires 30 days after creation; expired pages are
  access-restricted but **not deleted**, and can be extended once (30 more days) by the owner from the dashboard

The visitor-facing screen flow is planned as:

1. Intro (인트로) — typing animation + floating "?" particles, with a zodiac-animal character image shown next to
   the typed text if a due date was set; shows a "secret" placeholder screen instead if visited before the
   scheduled reveal time, or an "expired" screen if the retention period has passed
2. Guess selection (선택) — guest picks boy/girl before seeing the result (one guess per guest, dedup via cookie,
   not present in the reference site — a differentiator of this product)
3. Result reveal (결과 확인) — one of 3 owner-chosen reveal animations into a boy/girl themed scene:
   - **surprise box** (merges the reference site's gift-box open with a topper — box lid opens, a balloon rises,
     a message topper appears)
   - **cake** (cutting reveals pink/blue filling)
   - **balloon** (a large balloon pops, releasing colored mini-balloons/confetti)
   optionally with the same zodiac-year (띠) message and character image shown in the intro, plus whether the
   guest's own guess was correct.
   If BGM is enabled, it starts muted-autoplay on entering this screen with a fixed unmute icon; the unmuted
   preference persists across future visits (browser storage).
4. Leaving a congratulatory message (축하글 남기기) — guestbook entries (no dedup, identified by nickname only),
   moderatable from the admin dashboard

Page states: `secret` (pre-reveal) → `open` (reveal + guessing/guestbook live) → `expired` (retention over; data
retained, access-restricted, extendable once by the owner).

A reference site for the visual/animation style (intro overlay, gift-box reveal scene, boy/girl theming) is linked in
planning/Requirements.md: https://hyeminpark9105.github.io/baby-gender-reveal/ — note it's a static single-owner page
with no guessing, guestbook, or admin features; those are this project's additions on top of that presentation style.

## Working in this repo

### Backend commands (run from `api/`)

```bash
./gradlew test                                              # full suite
./gradlew test --tests "com.genderreveal.api.page.*"        # one package / class
./gradlew bootRun                                           # dev server on :8080, DB at ./data/gender-reveal.db
```

Docker is not installed on the dev machine, so nothing here is verified via docker-compose.

### Backend conventions worth knowing

- **Schema changes only via Flyway** (`src/main/resources/db/migration/V*.sql`); `ddl-auto` is `none`.
  SQLite runs with `foreign_keys=on` (both the app and test JDBC URLs), so tests that insert
  child rows must create a real parent `Page` first.
- **Time comes from an injected `Clock`** (`Instant.now(clock)`), never `Instant.now()` in services.
  Tests that need to move time import `MutableTestClockConfig` and cast the `Clock` to `MutableTestClock`.
  Every `Instant` column uses `InstantStringConverter` (fixed-width ISO text, so string ordering equals time ordering).
- **Page status (`secret`/`open`/`expired`) is computed at read time** by `PageStatusCalculator`, not stored.
- **SQLite unique-constraint violations surface as `JpaSystemException`** (a `DataAccessException`), *not*
  `DataIntegrityViolationException` — race backstops (`catch`) must catch `DataAccessException`.
- **Error handling**: named domain exceptions mapped by per-package `@RestControllerAdvice`; no generic
  catch-all handlers.
- **Guests are anonymous**: a `guest_id` UUID cookie (httpOnly, SameSite=Lax) identifies a guest for
  guessing/visit counts. Owners log in by email magic link and get an `owner_session` cookie; a page
  can only be created with a session, and other owners' pages return 404, not 403.
- Known deferred hardening: rate limiting on public write endpoints, guestbook pagination,
  Secure flag on the guest cookie (revisit with TLS/nginx), unified validation-error JSON shape;
  the frontend must escape guestbook messages.
- Known deferred hardening (frontend, `web/`): `tokens.test.ts` asserts against literals copied
  from the plan, not against `planning/design-system/project/tokens.json` itself — no automated
  cross-check against the design-system source of truth; `api.ts`'s `request()` success path throws
  an untyped `SyntaxError` on a non-JSON 2xx response body instead of a typed `ApiError` (asymmetric
  with the error path); `make-og.mjs`'s `.trim()` relies on sharp's default trim threshold against
  each source SVG's own rendered background rather than an explicit canvas background color — works
  today, could need adjustment if illustration assets change; the `open`-status page API response
  includes `actualGender` before a guest submits a guess, so a technically curious guest can read the
  answer via browser devtools before playing — accepted as a known limitation for this product's
  scale (frontend does not worsen this: no console logging, no storage writes, nothing renders it
  pre-reveal); `IntroScreen`'s 5s auto-advance timer cannot be paused/extended by the user (WCAG
  2.2.1 Timing Adjustable) — also, a very long nickname can make the typewriter animation outrun the
  fixed auto-advance delay; `submitGuess`'s `alreadyGuessed` return value is computed but not
  currently surfaced in the UI (a returning guest sees the same screen as a first-time guest).

### Working style used so far

Implementation follows the superpowers flow: brainstorm → spec → plan → subagent-driven execution in
a git worktree (per-task review loops, final whole-branch review, merge locally). Do not push to the
remote unless asked.

When editing SVG illustrations under `planning/design-system/project/assets/`, verify them by
actually rendering, not just by reading coordinates — a past pass shipped visibly broken art (hidden
ears, garbled shapes) that coordinate review alone didn't catch. `.claude/launch.json` has a
`design-system-preview` static server (serves `planning/design-system/`) for exactly this: start it
and open `http://localhost:8731/project/assets/<group>/<file>.svg` in the Browser pane.

## Work log

[planning/WORKLOG.md](planning/WORKLOG.md) is a running, dated log of requests and decisions made
across sessions (requirements changes, wireframe updates, etc.). When you complete meaningful work
in this repo, append a new dated entry to it (don't edit past entries) so the next session has that
history.

Claude Code also keeps its own session memory/transcripts outside this repo, under
`~/.claude/projects/...`. That's app-level state tied to this machine's Claude Code install, not a
project deliverable — it does not belong in this repo and isn't tracked here.

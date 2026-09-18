# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This directory (`gender-reveal/`) is a subproject of the `event-echo` repo, currently in the
**planning stage** — there's no source code, build tooling, or tests yet, and no build/lint/test
commands to run until implementation begins.

All planning-stage documents (requirements, wireframe, work log) live under [planning/](planning/),
kept separate so future implementation source code (in the repo root or its own `src/`) stays clean.
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

Since no framework, language, or package manager has been chosen yet, do not assume any specific
stack (e.g. React, Next.js) — confirm with the user before scaffolding, since that decision isn't
recorded anywhere in the repo yet.

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

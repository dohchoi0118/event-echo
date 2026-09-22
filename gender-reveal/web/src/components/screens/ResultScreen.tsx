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
  alreadyGuessed = false,
  revealDelayMs = 900,
}: {
  page: OpenPageView;
  guess: Gender;
  onNext: () => void;
  alreadyGuessed?: boolean;
  revealDelayMs?: number;
}) {
  const reduced = usePrefersReducedMotion();
  const [stage, setStage] = useState<Stage>('ready');
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

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
      {zodiac && <p className="text-body-lg">{`${zodiacLabelKo(zodiac)}둥이가 찾아왔어요!`}</p>}
      {page.message && <p className="text-body text-ink-muted">{page.message}</p>}
      {alreadyGuessed && (
        <p className="text-body-sm text-ink-muted">이미 참여하셨어요 · 이전 예측을 보여드려요</p>
      )}
      <p className="rounded-radius-full bg-surface-100 px-space-3 py-space-1 text-label">
        {guessSummary(guess, page.actualGender)}
      </p>
      <Button onClick={onNext}>다음: 축하글 남기기 ▶</Button>
    </Screen>
  );
}

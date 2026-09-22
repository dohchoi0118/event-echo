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
  autoAdvanceMs,
}: {
  nickname: string;
  zodiac: ZodiacKey | null;
  onNext: () => void;
  autoAdvanceMs?: number;
}) {
  const reduced = usePrefersReducedMotion();
  const text = `두근두근...\n${topic(nickname)} 딸일까요,\n아들일까요?`;
  const { shown } = useTypewriter(text, { instant: reduced });
  // Give the guest time to actually read the typed sentence: typing time + a fixed buffer,
  // so a long nickname doesn't get cut off by a fixed delay.
  const effectiveAutoAdvanceMs = autoAdvanceMs ?? text.length * 70 + 2000;

  useEffect(() => {
    const id = setTimeout(onNext, effectiveAutoAdvanceMs);
    return () => clearTimeout(id);
  }, [onNext, effectiveAutoAdvanceMs]);

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

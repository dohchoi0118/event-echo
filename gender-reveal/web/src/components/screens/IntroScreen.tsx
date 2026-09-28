'use client';

import { Button } from '../Button';
import { Screen } from '../Screen';
import { familySrc } from '@/lib/illustrations';

/** Parses the "YYYY-MM-DD" LocalDate string directly (no Date object) to avoid timezone shifts. */
function formatDueDate(dueDate: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dueDate);
  if (!match) return null;
  const [, year, month, day] = match;
  return `${year}년 ${Number(month)}월 ${Number(day)}일`;
}

export function IntroScreen({
  dueDate,
  onNext,
}: {
  dueDate: string | null;
  onNext: () => void;
}) {
  const formattedDueDate = dueDate ? formatDueDate(dueDate) : null;

  return (
    <Screen>
      <p className="text-label tracking-wide text-ink-muted">YOU&apos;RE INVITED</p>
      <p className="font-display text-display-lg">우리 가족에게 새로운 사랑이 찾아옵니다</p>
      <img src={familySrc} alt="뒷모습의 가족 일러스트" className="h-auto w-72" />
      <p className="text-body text-ink-muted">함께 축하해주세요</p>
      {formattedDueDate && <p className="text-body-lg">{formattedDueDate}</p>}
      <Button onClick={onNext}>탭해서 계속하기</Button>
    </Screen>
  );
}

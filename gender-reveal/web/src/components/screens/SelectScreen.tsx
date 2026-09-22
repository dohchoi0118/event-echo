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

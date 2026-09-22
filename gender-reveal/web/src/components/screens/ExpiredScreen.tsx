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

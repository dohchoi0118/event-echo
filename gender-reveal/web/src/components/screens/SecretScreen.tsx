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

import { Screen } from '../Screen';

export function ThankYouScreen() {
  return (
    <Screen>
      <svg aria-hidden viewBox="0 0 120 120" className="h-28 w-28">
        <ellipse cx="44" cy="46" rx="26" ry="32" fill="#F7C6D9" />
        <path d="M44 78v26" stroke="#D9A0BC" strokeWidth="2" fill="none" />
        <ellipse cx="82" cy="52" rx="24" ry="30" fill="#BFD3F2" />
        <path d="M82 82v22" stroke="#9AB3E0" strokeWidth="2" fill="none" />
      </svg>
      <p className="font-display text-display-lg">함께 축하해주셔서 감사해요</p>
      <div className="text-body text-ink-muted">
        <p>따뜻한 마음 덕분에 더 행복한 소식이 됐어요.</p>
        <p>건강한 모습으로 다시 만나요 :)</p>
      </div>
    </Screen>
  );
}

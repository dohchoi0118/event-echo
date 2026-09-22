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

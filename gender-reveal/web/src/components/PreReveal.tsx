import type { CSSProperties } from 'react';
import type { Gender, Theme } from '@/lib/types';

const STROKE = '#8C8579';
const FILL = '#F3EEE7';
const WHITE = '#FFFFFF';
const CONFETTI_COLORS = ['#6F86E6', '#E37AA6', '#F2793A', '#C79A4F', '#8C8579'];

/** Burst directions for the balloon theme's confetti, as (dx, dy) in SVG units from center. */
const CONFETTI_PIECES = [
  { dx: -60, dy: -40, delay: '0.35s' },
  { dx: -20, dy: -68, delay: '0.42s' },
  { dx: 30, dy: -64, delay: '0.38s' },
  { dx: 62, dy: -30, delay: '0.46s' },
  { dx: -50, dy: 20, delay: '0.5s' },
  { dx: 50, dy: 26, delay: '0.4s' },
  { dx: 0, dy: 50, delay: '0.44s' },
];

export function PreReveal({
  theme,
  gender,
  stage = 'ready',
}: {
  theme: Theme;
  gender: Gender;
  stage?: 'ready' | 'revealing';
}) {
  const accent = gender === 'boy' ? '#6F86E6' : '#E37AA6';
  const revealing = stage === 'revealing';

  return (
    <svg aria-hidden viewBox="0 0 200 200" className="h-48 w-48 overflow-visible">
      {theme === 'box' && (
        <g stroke={STROKE} strokeWidth="3" strokeLinejoin="round">
          <rect x="40" y="90" width="120" height="80" rx="8" fill={FILL} />
          <ellipse
            data-testid="box-balloon"
            cx="100"
            cy="118"
            rx="16"
            ry="20"
            fill={accent}
            style={{ transformOrigin: '100px 118px' }}
            className={revealing ? 'motion-safe:animate-balloon-rise' : 'opacity-0'}
          />
          <rect
            data-testid="box-topper"
            x="88"
            y="150"
            width="24"
            height="12"
            rx="4"
            fill={WHITE}
            style={{ transformOrigin: '100px 156px' }}
            className={revealing ? 'motion-safe:animate-topper-rise' : 'opacity-0'}
          />
          <g
            data-testid="box-lid"
            style={{ transformOrigin: '40px 94px' }}
            className={revealing ? 'motion-safe:animate-lid-open' : ''}
          >
            <rect x="32" y="66" width="136" height="28" rx="8" fill={WHITE} />
            <path d="M100 66v28" fill="none" />
            <path d="M100 66c-20-30-48-12-30 0M100 66c20-30 48-12 30 0" fill="none" />
          </g>
        </g>
      )}
      {theme === 'cake' && (
        <g stroke={STROKE} strokeWidth="3" strokeLinejoin="round">
          <rect x="30" y="110" width="140" height="50" rx="10" fill={FILL} />
          <rect data-testid="cake-filling" x="44" y="76" width="112" height="38" rx="10" fill={accent} />
          <g
            data-testid="cake-left"
            className={revealing ? 'motion-safe:animate-cake-slide-left' : ''}
            style={{ transformOrigin: '70px 95px' }}
          >
            <rect x="44" y="76" width="56" height="38" rx="10" fill={WHITE} />
          </g>
          <g
            data-testid="cake-right"
            className={revealing ? 'motion-safe:animate-cake-slide-right' : ''}
            style={{ transformOrigin: '130px 95px' }}
          >
            <rect x="100" y="76" width="56" height="38" rx="10" fill={WHITE} />
          </g>
          <g className={revealing ? 'motion-safe:animate-fade-out' : ''}>
            <path d="M100 76V56" fill="none" />
            <path d="M100 56c-6-8 0-14 0-14s6 6 0 14z" fill={FILL} />
          </g>
        </g>
      )}
      {theme === 'balloon' && (
        <>
          {revealing &&
            CONFETTI_PIECES.map((piece, i) => (
              <circle
                key={i}
                data-testid="confetti-piece"
                cx="100"
                cy="88"
                r="6"
                fill={CONFETTI_COLORS[i % CONFETTI_COLORS.length]}
                className="motion-safe:animate-confetti-burst"
                style={{ '--dx': `${piece.dx}px`, '--dy': `${piece.dy}px`, animationDelay: piece.delay } as CSSProperties}
              />
            ))}
          <g
            data-testid="balloon-body"
            stroke={STROKE}
            strokeWidth="3"
            strokeLinejoin="round"
            className={revealing ? 'motion-safe:animate-balloon-pop' : ''}
            style={{ transformOrigin: '100px 100px' }}
          >
            <ellipse cx="100" cy="88" rx="52" ry="62" fill={accent} />
            <path d="M92 150l8 10 8-10z" fill={accent} />
            <path d="M100 160c-10 14 10 20 0 34" fill="none" />
          </g>
        </>
      )}
    </svg>
  );
}

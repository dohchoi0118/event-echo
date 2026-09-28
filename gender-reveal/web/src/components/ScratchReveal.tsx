'use client';

import { useState } from 'react';
import type { ReactNode } from 'react';

/** Hides `children` behind a shaded, click-to-reveal cover — like a scratch-off lottery ticket.
 *  Used so the actual gender isn't casually visible, even to the page owner. */
export function ScratchReveal({ children, label = '클릭해서 보기' }: { children: ReactNode; label?: string }) {
  const [revealed, setRevealed] = useState(false);

  if (revealed) {
    return <>{children}</>;
  }

  return (
    <button
      type="button"
      onClick={() => setRevealed(true)}
      className="rounded-radius-sm bg-ink px-space-2 py-space-1 text-label text-surface-100"
    >
      {label}
    </button>
  );
}

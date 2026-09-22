'use client';

import { useEffect, useState } from 'react';

export function useTypewriter(
  text: string,
  { speedMs = 70, instant = false }: { speedMs?: number; instant?: boolean } = {},
): { shown: string; done: boolean } {
  const [count, setCount] = useState(instant ? text.length : 0);

  useEffect(() => {
    if (instant) {
      setCount(text.length);
      return;
    }
    setCount(0);
    let i = 0;
    const id = setInterval(() => {
      i += 1;
      setCount(i);
      if (i >= text.length) clearInterval(id);
    }, speedMs);
    return () => clearInterval(id);
  }, [text, speedMs, instant]);

  return { shown: text.slice(0, count), done: count >= text.length };
}

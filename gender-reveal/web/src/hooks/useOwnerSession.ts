'use client';

import { useEffect, useState } from 'react';
import { getMe } from '@/lib/api';

type SessionState = { status: 'loading' | 'authenticated' | 'unauthenticated'; email: string | null };

export function useOwnerSession(): SessionState {
  const [state, setState] = useState<SessionState>({ status: 'loading', email: null });

  useEffect(() => {
    let cancelled = false;
    getMe()
      .then(({ email }) => {
        if (!cancelled) setState({ status: 'authenticated', email });
      })
      .catch(() => {
        // Any failure (401, network, 5xx) is treated as "not logged in" — fail closed.
        if (!cancelled) setState({ status: 'unauthenticated', email: null });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}

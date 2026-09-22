'use client';

import { useEffect } from 'react';
import { CreatePageFlow } from '@/components/owner/CreatePageFlow';
import { useOwnerSession } from '@/hooks/useOwnerSession';

export default function Page() {
  const session = useOwnerSession();

  useEffect(() => {
    if (session.status === 'unauthenticated') {
      window.location.href = '/login';
    }
  }, [session.status]);

  if (session.status !== 'authenticated') {
    return null;
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[480px] flex-col px-space-3 py-space-5">
      <CreatePageFlow />
    </main>
  );
}

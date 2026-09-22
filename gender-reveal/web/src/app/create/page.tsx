'use client';

import { useEffect } from 'react';
import { CreatePageForm } from '@/components/owner/CreatePageForm';
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
      <CreatePageForm onSubmit={() => {}} submitting={false} error={null} />
    </main>
  );
}

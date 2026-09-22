'use client';

import { useEffect, useState } from 'react';
import { logout as apiLogout, listOwnerPages } from '@/lib/api';
import { useOwnerSession } from '@/hooks/useOwnerSession';
import type { OwnerPageSummary } from '@/lib/types';

const STATUS_LABEL: Record<OwnerPageSummary['status'], string> = {
  secret: '공개 대기',
  open: '공개 중',
  expired: '만료됨',
};

export function OwnerPageListScreen() {
  const session = useOwnerSession();
  const [pages, setPages] = useState<OwnerPageSummary[] | null>(null);

  useEffect(() => {
    if (session.status === 'unauthenticated') {
      window.location.href = '/login';
    }
  }, [session.status]);

  useEffect(() => {
    if (session.status === 'authenticated') {
      void listOwnerPages().then(setPages);
    }
  }, [session.status]);

  if (session.status !== 'authenticated') {
    return null;
  }

  const onLogout = async () => {
    await apiLogout();
    window.location.href = '/login';
  };

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[480px] flex-col gap-space-4 px-space-3 py-space-5">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-display-lg">내 페이지</h1>
          <p className="text-body-sm text-ink-muted">{session.email}</p>
        </div>
        <button type="button" onClick={onLogout} className="text-label text-ink-muted underline">
          로그아웃
        </button>
      </header>

      <button
        type="button"
        onClick={() => { window.location.href = '/create'; }}
        className="rounded-radius-full bg-accent-primary px-space-4 py-space-2 text-label text-surface-100"
      >
        새 페이지 만들기
      </button>

      {pages && pages.length === 0 && <p className="text-body text-ink-muted">아직 만든 페이지가 없어요</p>}

      <ul className="flex flex-col gap-space-2">
        {pages?.map((page) => (
          <li key={page.slug}>
            <button
              type="button"
              onClick={() => { window.location.href = `/dashboard?slug=${encodeURIComponent(page.slug)}`; }}
              className="flex w-full items-center justify-between rounded-radius-md border border-border bg-surface-100 p-space-3 text-left"
            >
              <span className="text-body-lg">{page.nickname}</span>
              <span className="rounded-radius-full bg-surface-200 px-space-2 py-space-1 text-label text-ink-muted">
                {STATUS_LABEL[page.status]}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </main>
  );
}

'use client';

import { useEffect, useState } from 'react';
import { getOwnerPageDetail, setActualGender } from '@/lib/api';
import { useOwnerSession } from '@/hooks/useOwnerSession';
import { genderKo } from '@/lib/result';
import { ScratchReveal } from '../ScratchReveal';
import type { Gender, OwnerPageDetail } from '@/lib/types';

export function slugFromSearch(search: string): string | null {
  return new URLSearchParams(search).get('slug');
}

export function GenderSelectScreen({ slug }: { slug: string }) {
  const session = useOwnerSession();
  const [detail, setDetail] = useState<OwnerPageDetail | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (session.status === 'unauthenticated') {
      window.location.href = '/login';
    }
  }, [session.status]);

  useEffect(() => {
    if (session.status === 'authenticated') {
      void getOwnerPageDetail(slug).then(setDetail).catch(() => setLoadError(true));
    }
  }, [session.status, slug]);

  const onSelect = async (gender: Gender) => {
    setSubmitting(true);
    setSubmitError(null);
    try {
      const updated = await setActualGender(slug, gender);
      setDetail(updated);
    } catch {
      setSubmitError('잠시 후 다시 시도해 주세요');
    } finally {
      setSubmitting(false);
    }
  };

  if (session.status !== 'authenticated') {
    return null;
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[420px] flex-col items-center gap-space-4 px-space-3 py-space-5 text-center">
      <a href={`/dashboard?slug=${slug}`} className="self-start text-label text-ink-muted">← 페이지로 돌아가기</a>
      <h1 className="font-display text-display-lg">성별 선택하기</h1>
      <p className="text-body text-ink-muted">
        여기서 고른 값은 소유자에게도 바로 보이지 않아요 — 클릭해야만 보여요.
      </p>

      {loadError && <p role="alert" className="text-body text-danger">불러오지 못했어요</p>}

      {detail && (
        <>
          {detail.actualGender !== null && (
            <p className="text-body-sm text-ink-muted">
              이미 선택됐어요: <ScratchReveal>{genderKo(detail.actualGender)}</ScratchReveal>
            </p>
          )}

          <div className="flex w-full gap-space-3">
            <button
              type="button"
              disabled={submitting}
              onClick={() => onSelect('boy')}
              className="flex-1 rounded-radius-md border border-border bg-surface-100 px-space-3 py-space-4 text-label disabled:opacity-50"
            >
              남아
            </button>
            <button
              type="button"
              disabled={submitting}
              onClick={() => onSelect('girl')}
              className="flex-1 rounded-radius-md border border-border bg-surface-100 px-space-3 py-space-4 text-label disabled:opacity-50"
            >
              여아
            </button>
          </div>

          {submitError && (
            <p role="alert" className="text-body-sm text-danger">{submitError}</p>
          )}
        </>
      )}
    </main>
  );
}

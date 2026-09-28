'use client';

import { useEffect, useState } from 'react';
import { getOwnerPageDetail, setActualGender } from '@/lib/api';
import { useOwnerSession } from '@/hooks/useOwnerSession';
import { genderKo } from '@/lib/result';
import { babySrc } from '@/lib/illustrations';
import { ScratchReveal } from '../ScratchReveal';
import type { Gender, OwnerPageDetail } from '@/lib/types';

export function slugFromSearch(search: string): string | null {
  return new URLSearchParams(search).get('slug');
}

const OPTIONS: { value: Gender; label: string }[] = [
  { value: 'boy', label: '남아' },
  { value: 'girl', label: '여아' },
];

export function GenderSelectScreen({ slug }: { slug: string }) {
  const session = useOwnerSession();
  const [detail, setDetail] = useState<OwnerPageDetail | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [selected, setSelected] = useState<Gender | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [justSaved, setJustSaved] = useState(false);

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

  const onConfirm = async () => {
    if (!selected) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const updated = await setActualGender(slug, selected);
      setDetail(updated);
      setJustSaved(true);
      setSelected(null);
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
        여기서 고른 값은 소유자에게도 바로 보이지 않아요 — 클릭해야만 보여요. 언제든 다시 와서 바꿀 수 있어요.
      </p>

      {loadError && <p role="alert" className="text-body text-danger">불러오지 못했어요</p>}

      {detail && (
        <>
          {detail.actualGender !== null && (
            <p className="text-body-sm text-ink-muted">
              {justSaved ? '저장했어요' : '이미 선택됐어요'}: <ScratchReveal>{genderKo(detail.actualGender)}</ScratchReveal>
            </p>
          )}

          <div className="flex w-full gap-space-3">
            {OPTIONS.map((option) => {
              const isSelected = selected === option.value;
              return (
                <button
                  key={option.value}
                  type="button"
                  disabled={submitting}
                  onClick={() => {
                    setSelected(option.value);
                    setJustSaved(false);
                  }}
                  className={`flex flex-1 flex-col items-center gap-space-2 rounded-radius-md border p-space-3 disabled:opacity-50 ${
                    isSelected ? 'border-accent-primary bg-surface-100' : 'border-border bg-surface-50'
                  }`}
                >
                  <img src={babySrc(option.value)} alt="" className="h-20 w-20" />
                  <span className="text-label">{option.label}</span>
                </button>
              );
            })}
          </div>

          <button
            type="button"
            disabled={!selected || submitting}
            onClick={onConfirm}
            className="rounded-radius-full bg-accent-primary px-space-4 py-space-2 text-label text-surface-100 disabled:opacity-50"
          >
            완료
          </button>

          {submitError && (
            <p role="alert" className="text-body-sm text-danger">{submitError}</p>
          )}
        </>
      )}
    </main>
  );
}

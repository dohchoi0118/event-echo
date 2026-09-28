'use client';

import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { deletePage, extendPage, getOwnerPageDetail, getOwnerStats } from '@/lib/api';
import { useOwnerSession } from '@/hooks/useOwnerSession';
import type { OwnerPageDetail, PageStats } from '@/lib/types';
import { genderKo } from '@/lib/result';
import { GuestbookModerationTable } from './GuestbookModerationTable';
import { ScratchReveal } from '../ScratchReveal';

export function slugFromSearch(search: string): string | null {
  return new URLSearchParams(search).get('slug');
}

export function OwnerPageDetailScreen({ slug }: { slug: string }) {
  const session = useOwnerSession();
  const [detail, setDetail] = useState<OwnerPageDetail | null>(null);
  const [stats, setStats] = useState<PageStats | null>(null);
  const [extending, setExtending] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    if (session.status === 'unauthenticated') {
      window.location.href = '/login';
    }
  }, [session.status]);

  const load = useCallback(() => {
    setLoadError(false);
    void getOwnerPageDetail(slug).then(setDetail).catch(() => setLoadError(true));
    void getOwnerStats(slug).then(setStats).catch(() => setLoadError(true));
  }, [slug]);

  useEffect(() => {
    if (session.status === 'authenticated') {
      load();
    }
  }, [session.status, load]);

  const onExtend = async () => {
    if (!window.confirm('한 번 연장하면 되돌릴 수 없어요. 30일을 연장할까요?')) {
      return;
    }
    setExtending(true);
    try {
      const updated = await extendPage(slug);
      // Apply the extend response directly rather than refetching via load(): extendPage
      // returns the authoritative post-extend fields (extended/expiresAt/revealAt/status), and
      // re-fetching getOwnerPageDetail here would be redundant with it (and, if that fetch ever
      // raced with something invalidating it, could momentarily show stale data).
      setDetail((prev) => (prev
        ? { ...prev, extended: updated.extended, expiresAt: updated.expiresAt, revealAt: updated.revealAt, status: updated.status }
        : prev));
    } finally {
      setExtending(false);
    }
  };

  const onDelete = async () => {
    if (!window.confirm('이 페이지와 모든 데이터(방문자·맞추기·방명록 기록)를 영구 삭제할까요? 되돌릴 수 없어요.')) {
      return;
    }
    setDeleting(true);
    setDeleteError(null);
    try {
      await deletePage(slug);
      window.location.href = '/dashboard';
    } catch {
      setDeleteError('잠시 후 다시 시도해 주세요');
      setDeleting(false);
    }
  };

  if (session.status !== 'authenticated') {
    return null;
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[480px] flex-col gap-space-4 px-space-3 py-space-5">
      <a href="/dashboard" className="text-label text-ink-muted">← 내 페이지</a>

      {loadError && (
        <p role="alert" className="text-body text-danger">페이지를 불러오지 못했어요</p>
      )}

      {!loadError && detail && (
        <>
          <div className="flex items-center justify-between gap-space-2">
            <h1 className="font-display text-display-lg">{detail.nickname}의 페이지</h1>
            <a
              href={`/g/${slug}`}
              target="_blank"
              rel="noopener noreferrer"
              className="shrink-0 rounded-radius-full border border-border px-space-3 py-space-1 text-label text-ink-muted"
            >
              방문자 페이지 보기
            </a>
          </div>

          {stats && (
            <section className="grid grid-cols-3 gap-space-2">
              <StatCard label="방문자 수" value={String(stats.visitors)} />
              <StatCard label="맞추기 참여자 수" value={`${stats.guessers}명`} />
              <StatCard label="예측 비율 (남아 / 여아)" value={`${stats.boyGuesses} / ${stats.girlGuesses}`} />
            </section>
          )}

          <section className="flex flex-col gap-space-2 rounded-radius-md border border-border bg-surface-100 p-space-3">
            <h2 className="font-display text-display-md">페이지 설정 / 보관주기</h2>
            <Row
              label="실제 성별"
              value={detail.actualGender === null ? (
                <a href={`/gender-select?slug=${slug}`} className="text-label text-accent-primary underline">
                  선택하기
                </a>
              ) : (
                <span className="flex items-center gap-space-2">
                  <ScratchReveal>{genderKo(detail.actualGender)}</ScratchReveal>
                  <a href={`/gender-select?slug=${slug}`} className="text-label text-ink-muted underline">
                    수정하기
                  </a>
                </span>
              )}
            />
            <Row label="공개 예정 일시" value={new Date(detail.revealAt).toLocaleString('ko-KR')} />
            <Row label="보관주기 만료일" value={new Date(detail.expiresAt).toLocaleDateString('ko-KR')} />
            <Row label="연장 사용 여부" value={detail.extended ? '사용함' : '미사용 (1회 가능)'} />
            {!detail.extended && (
              <button
                type="button"
                disabled={extending}
                onClick={onExtend}
                className="self-start rounded-radius-full bg-accent-primary px-space-3 py-space-1 text-label text-surface-100 disabled:opacity-50"
              >
                30일 연장하기
              </button>
            )}
          </section>

          <GuestbookModerationTable slug={slug} />

          <section className="flex flex-col gap-space-2 rounded-radius-md border border-danger p-space-3">
            <h2 className="font-display text-display-md text-danger">페이지 삭제</h2>
            <p className="text-body-sm text-ink-muted">
              페이지와 방문자·맞추기·방명록 기록을 모두 영구 삭제해요. 되돌릴 수 없어요.
            </p>
            <button
              type="button"
              disabled={deleting}
              onClick={onDelete}
              className="self-start rounded-radius-full border border-danger px-space-3 py-space-1 text-label text-danger disabled:opacity-50"
            >
              페이지 삭제
            </button>
            {deleteError && (
              <p role="alert" className="text-body-sm text-danger">{deleteError}</p>
            )}
          </section>
        </>
      )}
    </main>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-space-1 rounded-radius-md border border-border bg-surface-100 p-space-2 text-center">
      <span className="text-body-sm text-ink-muted">{label}</span>
      <span className="text-display-md font-display">{value}</span>
    </div>
  );
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between text-body">
      <span className="text-ink-muted">{label}</span>
      <span>{value}</span>
    </div>
  );
}

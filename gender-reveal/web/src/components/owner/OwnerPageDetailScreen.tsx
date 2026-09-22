'use client';

import { useCallback, useEffect, useState } from 'react';
import { extendPage, getOwnerPageDetail, getOwnerStats } from '@/lib/api';
import type { OwnerPageDetail, PageStats } from '@/lib/types';
import { genderKo } from '@/lib/result';

export function slugFromSearch(search: string): string | null {
  return new URLSearchParams(search).get('slug');
}

export function OwnerPageDetailScreen({ slug }: { slug: string }) {
  const [detail, setDetail] = useState<OwnerPageDetail | null>(null);
  const [stats, setStats] = useState<PageStats | null>(null);
  const [extending, setExtending] = useState(false);

  const load = useCallback(() => {
    void getOwnerPageDetail(slug).then(setDetail);
    void getOwnerStats(slug).then(setStats);
  }, [slug]);

  useEffect(() => load(), [load]);

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

  if (!detail || !stats) {
    return null;
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[480px] flex-col gap-space-4 px-space-3 py-space-5">
      <a href="/dashboard" className="text-label text-ink-muted">← 내 페이지</a>
      <h1 className="font-display text-display-lg">{detail.nickname}의 페이지</h1>

      <section className="grid grid-cols-3 gap-space-2">
        <StatCard label="방문자 수" value={String(stats.visitors)} />
        <StatCard label="맞추기 참여자 수" value={`${stats.guessers}명`} />
        <StatCard label="예측 비율 (남아 / 여아)" value={`${stats.boyGuesses} / ${stats.girlGuesses}`} />
      </section>

      <section className="flex flex-col gap-space-2 rounded-radius-md border border-border bg-surface-100 p-space-3">
        <h2 className="font-display text-display-md">페이지 설정 / 보관주기</h2>
        <Row label="실제 성별" value={genderKo(detail.actualGender)} />
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

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between text-body">
      <span className="text-ink-muted">{label}</span>
      <span>{value}</span>
    </div>
  );
}

'use client';

import { useCallback, useEffect, useState } from 'react';
import { deleteGuestbookEntry, getOwnerGuestbook, setGuestbookHidden } from '@/lib/api';
import { relativeTime } from '@/lib/time';
import { genderKo } from '@/lib/result';
import type { OwnerGuestbookEntry } from '@/lib/types';

export function GuestbookModerationTable({ slug, nowMs }: { slug: string; nowMs?: number }) {
  const [entries, setEntries] = useState<OwnerGuestbookEntry[] | null>(null);

  const load = useCallback(() => {
    void getOwnerGuestbook(slug).then(setEntries);
  }, [slug]);

  useEffect(() => load(), [load]);

  const onToggleHidden = async (entry: OwnerGuestbookEntry) => {
    const hidden = !entry.hidden;
    await setGuestbookHidden(slug, entry.id, hidden);
    // Update local state directly rather than refetching via load(): the mutation response
    // carries no updated list, and a refetch here would only be as fresh as the server's
    // eventual read, not the change just made.
    setEntries((prev) => (prev ? prev.map((e) => (e.id === entry.id ? { ...e, hidden } : e)) : prev));
  };

  const onDelete = async (entry: OwnerGuestbookEntry) => {
    if (!window.confirm('이 메시지를 삭제할까요? 되돌릴 수 없어요.')) {
      return;
    }
    await deleteGuestbookEntry(slug, entry.id);
    setEntries((prev) => (prev ? prev.filter((e) => e.id !== entry.id) : prev));
  };

  const now = nowMs ?? Date.now();

  return (
    <section className="flex flex-col gap-space-2 rounded-radius-md border border-border bg-surface-100 p-space-3">
      <h2 className="font-display text-display-md">방명록 관리</h2>
      {entries && entries.length === 0 && <p className="text-body text-ink-muted">아직 등록된 메시지가 없어요</p>}
      {entries && entries.length > 0 && (
        <table className="w-full text-left text-body-sm">
          <thead>
            <tr className="text-ink-muted">
              <th className="pb-space-1 font-normal">닉네임</th>
              <th className="pb-space-1 font-normal">메시지</th>
              <th className="pb-space-1 font-normal">등록일</th>
              <th className="pb-space-1 font-normal">상태</th>
              <th className="pb-space-1 font-normal">관리</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.id} className="border-t border-border align-top">
                <td className="py-space-2 pr-space-2">{entry.nickname}</td>
                <td className="py-space-2 pr-space-2">
                  <p className="whitespace-pre-line break-words text-body">{entry.message}</p>
                  {entry.guessedGender && (
                    <p className="text-body-sm text-ink-muted">
                      예측: {genderKo(entry.guessedGender)} · {entry.guessCorrect ? '정답' : '오답'}
                    </p>
                  )}
                </td>
                <td className="py-space-2 pr-space-2 text-ink-muted">{relativeTime(entry.createdAt, now)}</td>
                <td className="py-space-2 pr-space-2">
                  <span className="rounded-radius-full bg-surface-200 px-space-2 py-space-1 text-body-sm text-ink-muted">
                    {entry.hidden ? '숨김' : '노출'}
                  </span>
                </td>
                <td className="py-space-2">
                  <div className="flex gap-space-2">
                    <button type="button" onClick={() => onToggleHidden(entry)} className="underline">
                      {entry.hidden ? '노출 전환' : '숨김'}
                    </button>
                    <button type="button" onClick={() => onDelete(entry)} className="text-danger underline">
                      삭제
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

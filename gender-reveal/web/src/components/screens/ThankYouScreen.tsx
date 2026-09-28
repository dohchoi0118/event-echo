'use client';

import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '../Button';
import { getGuestbook, postGuestbook, ApiError } from '@/lib/api';
import { relativeTime } from '@/lib/time';
import type { GuestbookEntry } from '@/lib/types';

const inputClass =
  'w-full rounded-radius-sm border border-border bg-surface-100 px-space-2 py-space-2 text-body outline-none focus:border-accent-primary';

export function ThankYouScreen({ slug, nowMs }: { slug: string; nowMs?: number }) {
  const [entries, setEntries] = useState<GuestbookEntry[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [nickname, setNickname] = useState('');
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setEntries(await getGuestbook(slug));
      setLoadFailed(false);
    } catch {
      setLoadFailed(true);
    }
  }, [slug]);

  useEffect(() => {
    void load();
  }, [load]);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const trimmedNickname = nickname.trim();
    const trimmedMessage = message.trim();
    if (!trimmedNickname || !trimmedMessage) {
      setFormError('닉네임과 메시지를 입력해 주세요');
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      await postGuestbook(slug, { nickname: trimmedNickname, message: trimmedMessage });
      setMessage('');
      await load();
    } catch (error) {
      setFormError(
        error instanceof ApiError && error.status === 400
          ? '닉네임과 메시지를 입력해 주세요'
          : '잠시 후 다시 시도해 주세요',
      );
    } finally {
      setSubmitting(false);
    }
  };

  const now = nowMs ?? Date.now();

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[420px] flex-col items-center gap-space-4 px-space-3 py-space-5 text-center">
      <svg aria-hidden viewBox="0 0 120 120" className="h-28 w-28">
        <ellipse cx="44" cy="46" rx="26" ry="32" fill="#F7C6D9" />
        <path d="M44 78v26" stroke="#D9A0BC" strokeWidth="2" fill="none" />
        <ellipse cx="82" cy="52" rx="24" ry="30" fill="#BFD3F2" />
        <path d="M82 82v22" stroke="#9AB3E0" strokeWidth="2" fill="none" />
      </svg>
      <p className="font-display text-display-lg">함께 축하해주셔서 감사해요</p>
      <div className="text-body text-ink-muted">
        <p>따뜻한 마음 덕분에 더 행복한 소식이 됐어요.</p>
        <p>건강한 모습으로 다시 만나요 :)</p>
      </div>

      <form onSubmit={onSubmit} className="flex w-full flex-col gap-space-2 rounded-radius-md border border-border bg-surface-100 p-space-3 text-left">
        <h2 className="font-display text-display-md">축하 메시지 남기기</h2>
        <label className="flex flex-col gap-space-1 text-label">
          닉네임
          <input className={inputClass} value={nickname} maxLength={40} onChange={(e) => setNickname(e.target.value)} />
        </label>
        <label className="flex flex-col gap-space-1 text-label">
          메시지
          <textarea className={inputClass} rows={3} value={message} maxLength={500} onChange={(e) => setMessage(e.target.value)} />
        </label>
        {formError && (
          <p role="alert" className="text-body-sm text-danger">
            {formError}
          </p>
        )}
        <Button type="submit" disabled={submitting}>
          등록
        </Button>
      </form>

      <section className="flex w-full flex-col gap-space-2 text-left">
        <h2 className="font-display text-display-md">등록된 메시지</h2>
        {loadFailed && <p className="text-body text-ink-muted">메시지를 불러오지 못했어요</p>}
        {entries && entries.length === 0 && <p className="text-body text-ink-muted">아직 등록된 메시지가 없어요</p>}
        <ul className="flex flex-col gap-space-2">
          {entries?.map((entry, i) => (
            <li key={`${entry.createdAt}-${i}`} className="rounded-radius-md border border-border bg-surface-100 p-space-3">
              <p className="text-label text-ink-muted">
                {entry.nickname} · {relativeTime(entry.createdAt, now)}
              </p>
              <p className="mt-space-1 whitespace-pre-line break-words text-body">{entry.message}</p>
            </li>
          ))}
        </ul>
      </section>

      <p className="text-center text-body-sm text-ink-muted">
        닉네임으로만 구분되며 여러 번 남길 수 있어요 · 부적절한 글은 관리자가 숨길 수 있어요
      </p>

      <Button onClick={() => window.close()}>닫기</Button>
    </main>
  );
}

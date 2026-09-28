'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '../Button';
import { revealSrc } from '@/lib/illustrations';
import type { PageCreatePayload, Theme } from '@/lib/types';

const THEMES: { value: Theme; label: string }[] = [
  { value: 'box', label: '서프라이즈 박스' },
  { value: 'cake', label: '케이크' },
  { value: 'balloon', label: '풍선' },
];

const inputClass =
  'w-full rounded-radius-sm border border-border bg-surface-100 px-space-2 py-space-2 text-body outline-none focus:border-accent-primary';

export function CreatePageForm({
  onSubmit,
  submitting,
  error,
}: {
  onSubmit: (payload: PageCreatePayload) => void;
  submitting: boolean;
  error: string | null;
}) {
  const [nickname, setNickname] = useState('');
  const [revealAt, setRevealAt] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [message, setMessage] = useState('');
  const [theme, setTheme] = useState<Theme | null>(null);
  const [slug, setSlug] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!nickname.trim() || !revealAt || !theme) {
      setValidationError('태명, 공개 예정 일시, 테마를 입력해 주세요');
      return;
    }
    const trimmedSlug = slug.trim();
    if (trimmedSlug && !/^[a-z0-9-]{3,32}$/.test(trimmedSlug)) {
      setValidationError('커스텀 주소는 영문 소문자·숫자·하이픈 3~32자로 입력해 주세요');
      return;
    }
    const revealAtDate = new Date(revealAt);
    const maxRevealAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    if (revealAtDate >= maxRevealAt) {
      setValidationError('공개 예정 일시는 지금부터 30일 이내로 설정해 주세요');
      return;
    }
    setValidationError(null);
    onSubmit({
      nickname: nickname.trim(),
      revealAt: new Date(revealAt).toISOString(),
      dueDate: dueDate || null,
      message: message.trim() || null,
      theme,
      bgmEnabled: false,
      slug: slug.trim() || undefined,
    });
  };

  return (
    <form onSubmit={submit} className="flex w-full flex-col gap-space-3">
      <a href="/dashboard" className="text-label text-ink-muted">← 내 페이지</a>
      <h1 className="font-display text-display-lg">페이지 만들기</h1>

      <label className="flex flex-col gap-space-1 text-label">
        태명
        <input className={inputClass} value={nickname} onChange={(e) => setNickname(e.target.value)} />
      </label>

      <p className="rounded-radius-md bg-surface-200 px-space-3 py-space-2 text-body-sm text-ink-muted">
        실제 성별은 여기서 입력하지 않아요 — 발행 후 대시보드에서 별도로 설정해요.
      </p>

      <div className="flex flex-col gap-space-1">
        <label htmlFor="revealAt" className="text-label">
          공개 예정 일시
        </label>
        <input
          id="revealAt"
          type="datetime-local"
          className={inputClass}
          value={revealAt}
          onChange={(e) => setRevealAt(e.target.value)}
        />
        <span className="text-body-sm text-ink-muted">지금부터 30일 이내로 설정해 주세요</span>
      </div>

      <label className="flex flex-col gap-space-1 text-label">
        출산 예정일 (선택)
        <input type="date" className={inputClass} value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
      </label>

      <label className="flex flex-col gap-space-1 text-label">
        축하 메시지 (선택)
        <textarea className={inputClass} rows={3} value={message} onChange={(e) => setMessage(e.target.value)} />
      </label>

      <fieldset className="flex flex-col gap-space-1">
        <legend className="text-label">리빌 테마</legend>
        <div className="flex gap-space-2">
          {THEMES.map((t) => {
            const selected = theme === t.value;
            return (
              <label
                key={t.value}
                className={`flex flex-1 cursor-pointer flex-col items-center gap-space-1 rounded-radius-md border p-space-2 text-body-sm ${
                  selected ? 'border-accent-primary bg-surface-100' : 'border-border bg-surface-50'
                }`}
              >
                <input
                  type="radio"
                  name="theme"
                  checked={selected}
                  onChange={() => setTheme(t.value)}
                  className="sr-only"
                />
                <img src={revealSrc(t.value, 'boy')} alt="" className="h-16 w-16" />
                {t.label}
              </label>
            );
          })}
        </div>
      </fieldset>

      <div className="flex flex-col gap-space-1">
        <label htmlFor="slug" className="text-label">
          커스텀 주소 (선택)
        </label>
        <input id="slug" className={inputClass} value={slug} onChange={(e) => setSlug(e.target.value)} />
        <span className="text-body-sm text-ink-muted">영문 소문자·숫자·하이픈 3~32자, 비워두면 자동으로 만들어져요</span>
      </div>

      {(validationError || error) && (
        <p role="alert" className="text-body-sm text-danger">
          {validationError ?? error}
        </p>
      )}

      <Button type="submit" disabled={submitting}>
        미리보기
      </Button>
    </form>
  );
}

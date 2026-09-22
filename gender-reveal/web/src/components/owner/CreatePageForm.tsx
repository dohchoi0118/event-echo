'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '../Button';
import type { Gender, PageCreatePayload, Theme } from '@/lib/types';

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
  const [actualGender, setActualGender] = useState<Gender | null>(null);
  const [revealAt, setRevealAt] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [message, setMessage] = useState('');
  const [theme, setTheme] = useState<Theme | null>(null);
  const [slug, setSlug] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!nickname.trim() || !actualGender || !revealAt || !theme) {
      setValidationError('태명, 성별, 공개 예정 일시, 테마를 입력해 주세요');
      return;
    }
    setValidationError(null);
    onSubmit({
      nickname: nickname.trim(),
      actualGender,
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
      <h1 className="font-display text-display-lg">페이지 만들기</h1>

      <label className="flex flex-col gap-space-1 text-label">
        태명
        <input className={inputClass} value={nickname} onChange={(e) => setNickname(e.target.value)} />
      </label>

      <fieldset className="flex flex-col gap-space-1">
        <legend className="text-label">실제 성별</legend>
        <div className="flex gap-space-3">
          <label className="flex items-center gap-space-1">
            <input type="radio" name="actualGender" checked={actualGender === 'boy'} onChange={() => setActualGender('boy')} />
            남아
          </label>
          <label className="flex items-center gap-space-1">
            <input type="radio" name="actualGender" checked={actualGender === 'girl'} onChange={() => setActualGender('girl')} />
            여아
          </label>
        </div>
      </fieldset>

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
          {THEMES.map((t) => (
            <label key={t.value} className="flex items-center gap-space-1">
              <input type="radio" name="theme" checked={theme === t.value} onChange={() => setTheme(t.value)} />
              {t.label}
            </label>
          ))}
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

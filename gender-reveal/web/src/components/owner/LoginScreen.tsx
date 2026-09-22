'use client';

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '../Button';
import { Screen } from '../Screen';
import { requestMagicLink } from '@/lib/api';
import { useOwnerSession } from '@/hooks/useOwnerSession';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function LoginScreen() {
  const session = useOwnerSession();
  const [email, setEmail] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [callbackError, setCallbackError] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setCallbackError(new URLSearchParams(window.location.search).get('error') === 'invalid');
  }, []);

  // Already-logged-in owners land on the dashboard, not the login form. Mirrors the
  // useEffect+window.location.href pattern the plan uses for the opposite (unauthenticated)
  // redirect on owner-only screens.
  useEffect(() => {
    if (session.status === 'authenticated') {
      window.location.href = '/dashboard';
    }
  }, [session.status]);

  // While the session check is pending, or once it resolves authenticated (redirect effect above
  // is about to fire), render nothing rather than flashing the login form.
  if (session.status === 'loading' || session.status === 'authenticated') {
    return null;
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const trimmed = email.trim();
    if (!EMAIL_PATTERN.test(trimmed)) {
      setValidationError('올바른 이메일을 입력해 주세요');
      return;
    }
    setValidationError(null);
    setSubmitting(true);
    try {
      await requestMagicLink(trimmed);
    } catch {
      // Intentionally ignored: the API always means to say "check your mailbox",
      // regardless of whether the address exists or the send actually succeeded.
    } finally {
      setSubmitting(false);
      setSentTo(trimmed);
    }
  };

  if (sentTo) {
    return (
      <Screen>
        <p className="font-display text-display-md">메일함을 확인해 주세요</p>
        <p className="text-body text-ink-muted">{sentTo}로 로그인 링크를 보냈어요. 15분 동안 유효해요.</p>
      </Screen>
    );
  }

  return (
    <Screen>
      <h1 className="font-display text-display-lg">소유자 로그인</h1>
      <p className="text-body text-ink-muted">이메일로 로그인 링크를 보내드려요</p>
      {callbackError && (
        <p role="alert" className="text-body text-danger">
          링크가 만료되었거나 이미 사용됐어요. 다시 요청해 주세요.
        </p>
      )}
      <form onSubmit={onSubmit} noValidate className="flex w-full flex-col gap-space-2">
        <label className="flex flex-col gap-space-1 text-label">
          이메일
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-radius-sm border border-border bg-surface-100 px-space-2 py-space-2 text-body outline-none focus:border-accent-primary"
          />
        </label>
        {validationError && (
          <p role="alert" className="text-body-sm text-danger">
            {validationError}
          </p>
        )}
        <Button type="submit" disabled={submitting}>
          로그인 링크 받기
        </Button>
      </form>
    </Screen>
  );
}

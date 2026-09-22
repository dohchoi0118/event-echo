'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button } from './Button';
import { Screen } from './Screen';
import { ExpiredScreen } from './screens/ExpiredScreen';
import { GuestbookScreen } from './screens/GuestbookScreen';
import { IntroScreen } from './screens/IntroScreen';
import { ResultScreen } from './screens/ResultScreen';
import { SecretScreen } from './screens/SecretScreen';
import { SelectScreen } from './screens/SelectScreen';
import { ApiError, getPage, submitGuess } from '@/lib/api';
import type { Gender, PageView } from '@/lib/types';
import { zodiacFromDueDate } from '@/lib/zodiac';

type Load = { kind: 'loading' } | { kind: 'notfound' } | { kind: 'error' } | { kind: 'ready'; view: PageView };
type Stage = 'intro' | 'select' | 'result' | 'guestbook';

/** The static shell is served for every /g/<slug>; the slug only exists in the browser URL. */
export function slugFromPathname(pathname: string): string | null {
  const match = /^\/g\/([^/]+)\/?$/.exec(pathname);
  return match ? decodeURIComponent(match[1]) : null;
}

export function GuestPage() {
  const [slug, setSlug] = useState<string | null | undefined>(undefined);
  const [load, setLoad] = useState<Load>({ kind: 'loading' });
  const [stage, setStage] = useState<Stage>('intro');
  const [guess, setGuess] = useState<Gender | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [selectError, setSelectError] = useState<string | null>(null);
  const [guestbookEnteredAtMs, setGuestbookEnteredAtMs] = useState<number | null>(null);

  useEffect(() => {
    setSlug(slugFromPathname(window.location.pathname));
  }, []);

  const loadPage = useCallback(async (currentSlug: string) => {
    setLoad({ kind: 'loading' });
    try {
      setLoad({ kind: 'ready', view: await getPage(currentSlug) });
    } catch (error) {
      setLoad(error instanceof ApiError && error.status === 404 ? { kind: 'notfound' } : { kind: 'error' });
    }
  }, []);

  useEffect(() => {
    if (slug) void loadPage(slug);
  }, [slug, loadPage]);

  const goToSelect = useCallback(() => setStage('select'), []);

  const onSelect = async (gender: Gender) => {
    if (!slug) return;
    setSubmitting(true);
    setSelectError(null);
    try {
      const result = await submitGuess(slug, gender);
      setGuess(result.guessedGender);
      setStage('result');
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setStage('intro');
        await loadPage(slug); // the page is no longer open (e.g. it expired meanwhile)
      } else {
        setSelectError('잠시 후 다시 시도해 주세요');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (slug === undefined || load.kind === 'loading') {
    return slug === null ? <NotFound /> : <Screen><p className="text-body text-ink-muted">불러오는 중…</p></Screen>;
  }
  if (slug === null || load.kind === 'notfound') return <NotFound />;
  if (load.kind === 'error') {
    return (
      <Screen>
        <p className="text-body">잠시 후 다시 시도해 주세요</p>
        <Button onClick={() => void loadPage(slug)}>다시 시도</Button>
      </Screen>
    );
  }

  const { view } = load;
  if (view.status === 'secret') return <SecretScreen nickname={view.nickname} />;
  if (view.status === 'expired') return <ExpiredScreen />;

  if (stage === 'intro') {
    return <IntroScreen nickname={view.nickname} zodiac={zodiacFromDueDate(view.dueDate)} onNext={goToSelect} />;
  }
  if (stage === 'select' || !guess) {
    return <SelectScreen onSelect={onSelect} submitting={submitting} error={selectError} />;
  }
  if (stage === 'result') {
    return (
      <ResultScreen
        page={view}
        guess={guess}
        onNext={() => {
          setGuestbookEnteredAtMs(Date.now());
          setStage('guestbook');
        }}
      />
    );
  }
  return <GuestbookScreen slug={slug} nowMs={guestbookEnteredAtMs ?? undefined} />;
}

function NotFound() {
  return (
    <Screen>
      <p className="font-display text-display-md">존재하지 않는 페이지예요</p>
      <p className="text-body text-ink-muted">주소를 다시 확인해 주세요</p>
    </Screen>
  );
}

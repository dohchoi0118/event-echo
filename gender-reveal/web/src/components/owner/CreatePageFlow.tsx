'use client';

import { useState } from 'react';
import { CreatePageForm } from './CreatePageForm';
import { CreatePagePreview } from './CreatePagePreview';
import { PublishSuccessScreen } from './PublishSuccessScreen';
import { ApiError, checkSlugAvailability, createPage } from '@/lib/api';
import type { PageCreatePayload } from '@/lib/types';

type Stage = { kind: 'form' } | { kind: 'preview'; payload: PageCreatePayload } | { kind: 'done'; slug: string };

export function CreatePageFlow() {
  const [stage, setStage] = useState<Stage>({ kind: 'form' });
  const [submitting, setSubmitting] = useState(false);
  const [checkingSlug, setCheckingSlug] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const proceedToPreview = async (payload: PageCreatePayload) => {
    if (!payload.slug) {
      setStage({ kind: 'preview', payload });
      return;
    }
    setCheckingSlug(true);
    setFormError(null);
    try {
      const available = await checkSlugAvailability(payload.slug);
      if (!available) {
        setFormError('이미 사용 중인 주소예요');
        return;
      }
      setStage({ kind: 'preview', payload });
    } catch {
      setFormError('잠시 후 다시 시도해 주세요');
    } finally {
      setCheckingSlug(false);
    }
  };

  const publish = async (payload: PageCreatePayload) => {
    setSubmitting(true);
    try {
      const { slug } = await createPage(payload);
      setStage({ kind: 'done', slug });
    } catch (error) {
      let message = '잠시 후 다시 시도해 주세요';
      if (error instanceof ApiError && error.status === 409) {
        message = '이미 사용 중인 주소예요';
      } else if (error instanceof ApiError && error.status === 400) {
        message = '입력값을 다시 확인해 주세요';
      }
      setFormError(message);
      setStage({ kind: 'form' });
    } finally {
      setSubmitting(false);
    }
  };

  if (stage.kind === 'done') {
    return <PublishSuccessScreen slug={stage.slug} />;
  }

  return (
    <>
      <div hidden={stage.kind !== 'form'}>
        <CreatePageForm
          onSubmit={proceedToPreview}
          submitting={submitting || checkingSlug}
          error={formError}
        />
      </div>
      {stage.kind === 'preview' && (
        <CreatePagePreview
          payload={stage.payload}
          onEdit={() => setStage({ kind: 'form' })}
          onPublish={() => publish(stage.payload)}
          submitting={submitting}
        />
      )}
    </>
  );
}

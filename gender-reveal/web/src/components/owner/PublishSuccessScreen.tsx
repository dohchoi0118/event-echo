'use client';

import { useState } from 'react';
import { Button } from '../Button';

export function PublishSuccessScreen({ slug }: { slug: string }) {
  const [copied, setCopied] = useState(false);
  const link = `${window.location.origin}/g/${slug}`;

  const onCopy = async () => {
    await navigator.clipboard.writeText(link);
    setCopied(true);
  };

  return (
    <div className="flex w-full flex-col items-center gap-space-3 text-center">
      <p className="font-display text-display-lg">페이지가 발행됐어요!</p>
      <p className="text-body text-ink-muted">이메일로도 링크를 보내드렸어요</p>
      <p className="break-all rounded-radius-md bg-surface-200 px-space-3 py-space-2 text-body">{link}</p>
      <Button onClick={onCopy}>{copied ? '복사했어요' : '링크 복사'}</Button>
      <a href="/dashboard" className="text-label text-ink-muted underline">
        대시보드로 이동
      </a>
    </div>
  );
}

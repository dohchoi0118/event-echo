import { Button } from '../Button';
import { babySrc } from '@/lib/illustrations';
import { REVEAL_PROMPT } from '@/lib/result';
import { zodiacFromDueDate, zodiacLabelKo } from '@/lib/zodiac';
import type { PageCreatePayload } from '@/lib/types';

export function CreatePagePreview({
  payload,
  onEdit,
  onPublish,
  submitting,
}: {
  payload: PageCreatePayload;
  onEdit: () => void;
  onPublish: () => void;
  submitting: boolean;
}) {
  const zodiac = zodiacFromDueDate(payload.dueDate);

  return (
    <div className="flex w-full flex-col gap-space-3">
      <button type="button" onClick={onEdit} className="self-start text-label text-ink-muted">
        ← 다시 입력하기
      </button>
      <h1 className="font-display text-display-lg">이렇게 만들어져요</h1>
      <div className="flex flex-col items-center gap-space-2 rounded-radius-md border border-border bg-surface-100 p-space-4">
        <img src={babySrc(payload.actualGender)} alt="" className="h-24 w-24" />
        <p className="text-body-lg">{payload.nickname}</p>
        {zodiac && <p className="text-body-sm text-ink-muted">{zodiacLabelKo(zodiac)}</p>}
        <p className="text-body">{REVEAL_PROMPT[payload.theme]}</p>
        {payload.message && <p className="text-body text-ink-muted">{payload.message}</p>}
      </div>
      <p className="text-body-sm text-ink-muted">발행 후 실제 화면에서 확인해 보세요</p>
      <Button onClick={onPublish} disabled={submitting}>
        발행하기
      </Button>
    </div>
  );
}

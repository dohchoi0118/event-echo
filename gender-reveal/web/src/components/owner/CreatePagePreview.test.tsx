import { render, screen } from '@testing-library/react';
import { CreatePagePreview } from './CreatePagePreview';
import type { PageCreatePayload } from '@/lib/types';

const basePayload: PageCreatePayload = {
  nickname: '뽀튼이',
  revealAt: '2026-10-01T09:00:00.000Z',
  dueDate: null,
  message: null,
  theme: 'box',
  bgmEnabled: false,
  slug: undefined,
};

describe('CreatePagePreview', () => {
  it('never shows the actual gender — it is not known at creation time', () => {
    render(
      <CreatePagePreview payload={basePayload} onEdit={() => {}} onPublish={() => {}} submitting={false} />,
    );

    expect(screen.getByText('실제 성별은 발행 후 별도로 설정해요')).toBeInTheDocument();
    expect(screen.queryByText('남아')).not.toBeInTheDocument();
    expect(screen.queryByText('여아')).not.toBeInTheDocument();
  });
});

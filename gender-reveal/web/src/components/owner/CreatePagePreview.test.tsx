import { render, screen } from '@testing-library/react';
import { CreatePagePreview } from './CreatePagePreview';
import type { PageCreatePayload } from '@/lib/types';

const basePayload: PageCreatePayload = {
  nickname: '뽀튼이',
  actualGender: 'boy',
  revealAt: '2026-10-01T09:00:00.000Z',
  dueDate: null,
  message: null,
  theme: 'box',
  bgmEnabled: false,
  slug: undefined,
};

describe('CreatePagePreview', () => {
  it('shows the gender as text for a boy', () => {
    render(
      <CreatePagePreview payload={basePayload} onEdit={() => {}} onPublish={() => {}} submitting={false} />,
    );

    expect(screen.getByText('남아')).toBeInTheDocument();
  });

  it('shows the gender as text for a girl', () => {
    render(
      <CreatePagePreview
        payload={{ ...basePayload, actualGender: 'girl' }}
        onEdit={() => {}}
        onPublish={() => {}}
        submitting={false}
      />,
    );

    expect(screen.getByText('여아')).toBeInTheDocument();
  });
});

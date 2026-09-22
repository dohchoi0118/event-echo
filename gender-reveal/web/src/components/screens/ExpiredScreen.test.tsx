import { render, screen } from '@testing-library/react';
import { ExpiredScreen } from './ExpiredScreen';

describe('ExpiredScreen', () => {
  it('explains the restriction and links owners to login', () => {
    render(<ExpiredScreen />);

    expect(screen.getByText('열람이 제한된 페이지예요')).toBeInTheDocument();
    expect(screen.getByText('보관주기(30일)가 지나 더 이상')).toBeInTheDocument();
    expect(screen.getByText('데이터는 삭제되지 않았어요 — 소유자가')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '소유자 로그인 (매직링크)' })).toHaveAttribute('href', '/login');
  });
});

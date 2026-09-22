import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SelectScreen } from './SelectScreen';

describe('SelectScreen', () => {
  it('reports the chosen gender', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<SelectScreen onSelect={onSelect} submitting={false} error={null} />);

    expect(screen.getByText('당신의 예상은?')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '남자 아기' }));
    await user.click(screen.getByRole('button', { name: '여자 아기' }));

    expect(onSelect).toHaveBeenNthCalledWith(1, 'boy');
    expect(onSelect).toHaveBeenNthCalledWith(2, 'girl');
    expect(screen.getByText('한 번만 참여할 수 있어요 (쿠키 기준, 로그인 불필요)')).toBeInTheDocument();
  });

  it('disables both choices while submitting', () => {
    render(<SelectScreen onSelect={() => {}} submitting error={null} />);

    expect(screen.getByRole('button', { name: '남자 아기' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '여자 아기' })).toBeDisabled();
  });

  it('shows an error message as an alert', () => {
    render(<SelectScreen onSelect={() => {}} submitting={false} error="잠시 후 다시 시도해 주세요" />);

    expect(screen.getByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
  });
});

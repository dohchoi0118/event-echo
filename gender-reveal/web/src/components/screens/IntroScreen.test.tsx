import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { IntroScreen } from './IntroScreen';

describe('IntroScreen', () => {
  it('shows the fixed invitation copy and the formatted due date', () => {
    render(<IntroScreen dueDate="2026-11-03" onNext={() => {}} />);

    expect(screen.getByText('우리 가족에게 새로운 사랑이 찾아옵니다')).toBeInTheDocument();
    expect(screen.getByText('함께 축하해주세요')).toBeInTheDocument();
    expect(screen.getByText('2026년 11월 3일')).toBeInTheDocument();
    expect(screen.getByAltText('뒷모습의 가족 일러스트')).toHaveAttribute('src', '/illustrations/family.svg');
  });

  it('omits the date line when there is no due date', () => {
    render(<IntroScreen dueDate={null} onNext={() => {}} />);

    expect(screen.queryByText(/\d{4}년 \d+월 \d+일/)).not.toBeInTheDocument();
  });

  it('advances on tap', async () => {
    const onNext = vi.fn();
    const user = userEvent.setup();
    render(<IntroScreen dueDate={null} onNext={onNext} />);

    await user.click(screen.getByRole('button', { name: '탭해서 계속하기' }));

    expect(onNext).toHaveBeenCalledTimes(1);
  });
});

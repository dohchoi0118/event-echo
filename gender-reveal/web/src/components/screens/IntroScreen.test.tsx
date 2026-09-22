import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { IntroScreen } from './IntroScreen';

beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
afterEach(() => vi.useRealTimers());

describe('IntroScreen', () => {
  it('types the question with the right topic particle', () => {
    render(<IntroScreen nickname="뽀튼이" zodiac={null} onNext={() => {}} />);

    act(() => { vi.advanceTimersByTime(3000); });

    const text = screen.getByTestId('intro-text');
    expect(text.textContent).toBe('두근두근...\n뽀튼이는 딸일까요,\n아들일까요?');
  });

  it('uses 은 after a final consonant', () => {
    render(<IntroScreen nickname="별" zodiac={null} onNext={() => {}} />);

    act(() => { vi.advanceTimersByTime(3000); });

    expect(screen.getByTestId('intro-text').textContent).toContain('별은 딸일까요,');
  });

  it('shows the zodiac character only when a zodiac is given', () => {
    const { rerender } = render(<IntroScreen nickname="뽀튼이" zodiac={null} onNext={() => {}} />);
    expect(screen.queryByRole('img', { name: '말띠' })).not.toBeInTheDocument();

    rerender(<IntroScreen nickname="뽀튼이" zodiac="horse" onNext={() => {}} />);
    expect(screen.getByRole('img', { name: '말띠' })).toHaveAttribute('src', '/illustrations/zodiac/horse.svg');
  });

  it('advances when the continue button is pressed', async () => {
    const onNext = vi.fn();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<IntroScreen nickname="뽀튼이" zodiac={null} onNext={onNext} />);

    await user.click(screen.getByRole('button', { name: '탭해서 계속하기' }));

    expect(onNext).toHaveBeenCalledTimes(1);
  });

  it('advances by itself after the auto-advance delay', () => {
    const onNext = vi.fn();
    render(<IntroScreen nickname="뽀튼이" zodiac={null} onNext={onNext} autoAdvanceMs={5000} />);

    act(() => { vi.advanceTimersByTime(4999); });
    expect(onNext).not.toHaveBeenCalled();
    act(() => { vi.advanceTimersByTime(1); });
    expect(onNext).toHaveBeenCalledTimes(1);
  });
});
